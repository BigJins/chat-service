package allmart.chatservice.application;

import allmart.chatservice.adapter.client.OrderCreateException;
import allmart.chatservice.adapter.client.dto.OrderConfirmData;
import allmart.chatservice.adapter.client.dto.OrderCreateResult;
import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import allmart.chatservice.application.provided.ChatUseCase;
import allmart.chatservice.application.required.AddressPort;
import allmart.chatservice.application.required.OrderCreationPort;
import allmart.chatservice.application.tool.ChatToolExecutor;
import allmart.chatservice.application.tool.ChatTools;
import allmart.chatservice.domain.prompt.SystemPromptBuilder;
import allmart.chatservice.domain.session.ChatSession;
import allmart.chatservice.domain.session.ChatSessionStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService implements ChatUseCase {

    private final ChatClient chatClient;
    private final OrderCreationPort orderCreationPort;
    private final OrderConfirmParser orderConfirmParser;
    private final ChatSessionStore sessionStore;
    private final SystemPromptBuilder promptBuilder;
    private final ChatToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final AddressPort addressPort;

    /** 도구 호출 중 검색된 상품 목록 임시 저장 (buyerId → results) */
    private final ConcurrentHashMap<Long, List<ProductSearchResult>> pendingProductLists = new ConcurrentHashMap<>();

    // ── 인바운드 포트 구현 ────────────────────────────────────────────

    @Override
    public Flux<ServerSentEvent<String>> stream(Long buyerId, String message) {
        ChatSession session = sessionStore.getOrCreate(buyerId);

        // 최초 요청 시 기본 배송지 사전 적재 — RestClient 블로킹이므로 boundedElastic에서 실행
        Mono<Void> preload = (session.getDeliveryAddress() == null && !session.isAddressPreloaded())
                ? Mono.fromRunnable(() -> {
                    session.markAddressPreloaded();
                    preloadDefaultAddress(buyerId, session);
                  }).subscribeOn(Schedulers.boundedElastic()).then()
                : Mono.empty();

        return preload.thenMany(Flux.defer(() -> buildStream(buyerId, message, session)))
                .onErrorResume(e -> {
                    log.error("스트림 최상위 오류: buyerId={}, error={}", buyerId, e.getMessage(), e);
                    return Flux.just(errorEvent("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."));
                });
    }

    @Override
    public void resetSession(Long buyerId) {
        sessionStore.remove(buyerId);
        log.info("세션 초기화: buyerId={}", buyerId);
    }

    // ── 스트림 조립 ───────────────────────────────────────────────────

    private Flux<ServerSentEvent<String>> buildStream(Long buyerId, String message, ChatSession session) {
        String systemPrompt = promptBuilder.build(session.getCartItems(), session.getDeliveryAddress());
        StringJoiner accumulator = new StringJoiner("");

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(MessageChatMemoryAdvisor.builder(session.getChatMemory())
                        .conversationId(ChatSession.CONVERSATION_ID)
                        .build())
                .tools(new ChatTools(buyerId, session, toolExecutor, addressPort, pendingProductLists))
                .stream()
                .content()
                .doOnNext(accumulator::add)
                .map(ChatService::textEvent)
                .concatWith(Mono.fromSupplier(accumulator::toString)
                        .flatMapMany(fullText -> postProcess(buyerId, fullText)))
                .onErrorResume(e -> {
                    log.error("채팅 스트림 오류: buyerId={}, error={}", buyerId, e.getMessage());
                    return Flux.just(errorEvent("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."));
                });
    }

    private Flux<ServerSentEvent<String>> postProcess(Long buyerId, String fullText) {
        log.debug("buyerId={} 응답 완료, 길이={}", buyerId, fullText.length());
        return Flux.concat(handleProductList(buyerId), handleOrderConfirm(fullText, buyerId));
    }

    // ── auth-service 기본 배송지 사전 적재 ──────────────────────────────

    private void preloadDefaultAddress(Long buyerId, ChatSession session) {
        addressPort.getDefaultAddress(buyerId).ifPresent(addr -> {
            session.saveDeliveryAddress(addr.zipCode(), addr.roadAddress(), addr.detailAddress(), null);
            log.debug("기본 배송지 자동 적용: buyerId={}, address={}", buyerId, addr.roadAddress());
        });
    }

    // ── 상품 목록 SSE 이벤트 ─────────────────────────────────────────

    private Flux<ServerSentEvent<String>> handleProductList(Long buyerId) {
        List<ProductSearchResult> results = pendingProductLists.remove(buyerId);
        if (results == null || results.isEmpty()) return Flux.empty();
        try {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("product_list")
                    .data(objectMapper.writeValueAsString(results))
                    .build());
        } catch (JacksonException e) {
            log.warn("product_list 직렬화 실패: buyerId={}", buyerId);
            return Flux.empty();
        }
    }

    // ── 주문 확정 처리 ────────────────────────────────────────────────

    private Flux<ServerSentEvent<String>> handleOrderConfirm(String fullText, Long buyerId) {
        Optional<OrderConfirmData> confirmOpt = orderConfirmParser.parse(fullText);
        if (confirmOpt.isEmpty()) return Flux.empty();

        // ORDER_CONFIRM 감지 즉시 세션 리셋 — 다음 턴 중복 주문 방지
        sessionStore.remove(buyerId);
        log.info("주문 확정 감지, 세션 리셋: buyerId={}", buyerId);

        OrderConfirmData confirm = confirmOpt.get();
        return Mono.fromCallable(() -> orderCreationPort.createOrder(confirm, buyerId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ServerSentEvent.<String>builder()
                        .event("order_created")
                        .data(toOrderCreatedJson(result, confirm))
                        .build())
                .onErrorResume(OrderCreateException.class, e -> {
                    log.warn("주문 생성 실패(클라이언트 오류): buyerId={}, status={}", buyerId, e.getStatusCode());
                    return Mono.just(textEvent(e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.error("주문 생성 실패: buyerId={}, error={}", buyerId, e.getMessage());
                    return Mono.just(errorEvent("주문 처리 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요."));
                })
                .flux();
    }

    // ── SSE 이벤트 헬퍼 ──────────────────────────────────────────────

    private static ServerSentEvent<String> textEvent(String data) {
        return ServerSentEvent.<String>builder().event("text").data(data).build();
    }

    private static ServerSentEvent<String> errorEvent(String data) {
        return ServerSentEvent.<String>builder().event("error").data(data).build();
    }

    // ── JSON 직렬화 ───────────────────────────────────────────────────

    private String toOrderCreatedJson(OrderCreateResult result, OrderConfirmData confirm) {
        try {
            List<OrderConfirmData.OrderLineData> lines = confirm.orderLines();
            String orderName = lines.isEmpty() ? "주문"
                    : lines.size() == 1 ? lines.get(0).productNameSnapshot()
                    : lines.get(0).productNameSnapshot() + " 외 " + (lines.size() - 1) + "건";

            tools.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("orderId",     String.valueOf(result.orderId()));
            node.put("tossOrderId", result.tossOrderId());
            node.put("amount",      result.amount());
            node.put("payMethod",   confirm.payMethod());
            node.put("orderName",   orderName);
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            return "{\"orderId\":\"" + result.orderId() + "\",\"tossOrderId\":\"" + result.tossOrderId()
                    + "\",\"amount\":" + result.amount() + ",\"payMethod\":\"" + confirm.payMethod() + "\"}";
        }
    }
}
