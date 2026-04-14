package allmart.chatservice.application;

import allmart.chatservice.adapter.client.AuthServiceClient;
import allmart.chatservice.adapter.client.OrderCreateException;
import allmart.chatservice.adapter.client.OrderServiceClient;
import allmart.chatservice.adapter.client.dto.OrderConfirmData;
import allmart.chatservice.adapter.client.dto.OrderCreateResult;
import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import allmart.chatservice.adapter.client.dto.SavedAddressInfo;
import allmart.chatservice.domain.prompt.SystemPromptBuilder;
import allmart.chatservice.domain.session.ChatMessage;
import allmart.chatservice.domain.session.ChatSession;
import allmart.chatservice.domain.session.ChatSessionStore;
import allmart.chatservice.application.tool.ChatToolExecutor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
    private final OrderServiceClient orderServiceClient;
    private final OrderConfirmParser orderConfirmParser;
    private final ChatSessionStore sessionStore;
    private final SystemPromptBuilder promptBuilder;
    private final ChatToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;

    /** 도구 호출 중 검색된 상품 목록을 임시 저장 (buyerId → results, 응답 완료 후 SSE 발행) */
    private final ConcurrentHashMap<Long, List<ProductSearchResult>> pendingProductLists = new ConcurrentHashMap<>();

    @Override
    public Flux<ServerSentEvent<String>> stream(Long buyerId, String message) {
        ChatSession session = sessionStore.getOrCreate(buyerId);

        // 첫 턴에만 auth-service 기본 배송지를 세션에 자동 적용
        if (session.getDeliveryAddress() == null && session.getMessages().isEmpty()) {
            preloadDefaultAddress(buyerId, session);
        }

        session.addUserMessage(message);

        List<Message> history = buildSpringAiMessages(session);
        String systemPrompt = promptBuilder.build(session.getCartItems(), session.getDeliveryAddress());
        StringJoiner accumulator = new StringJoiner("");

        return chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .toolCallbacks(buildToolCallbacks(buyerId, session))
                .stream()
                .content()
                .doOnNext(accumulator::add)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("text")
                        .data(chunk)
                        .build())
                .concatWith(Mono.fromSupplier(accumulator::toString)
                        .flatMapMany(fullText -> {
                            session.addAssistantMessage(fullText);
                            log.debug("buyerId={} 응답 완료, 길이={}", buyerId, fullText.length());
                            return Flux.concat(
                                    handleProductList(buyerId),
                                    handleOrderConfirm(fullText, buyerId)
                            );
                        }))
                .onErrorResume(e -> {
                    log.error("채팅 스트림 오류: buyerId={}, error={}", buyerId, e.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.")
                            .build());
                });
    }

    @Override
    public void resetSession(Long buyerId) {
        sessionStore.remove(buyerId);
        log.info("세션 초기화: buyerId={}", buyerId);
    }

    // --- auth-service 기본 배송지 사전 적재 ---

    private void preloadDefaultAddress(Long buyerId, ChatSession session) {
        try {
            SavedAddressInfo addr = authServiceClient.getDefaultAddress(buyerId);
            if (addr != null && addr.roadAddress() != null && !addr.roadAddress().isBlank()) {
                session.saveDeliveryAddress(addr.zipCode(), addr.roadAddress(), addr.detailAddress(), null);
                log.debug("기본 배송지 자동 적용: buyerId={}, address={}", buyerId, addr.roadAddress());
            }
        } catch (Exception e) {
            log.warn("기본 배송지 조회 실패(무시): buyerId={}, reason={}", buyerId, e.getMessage());
        }
    }

    // --- Spring AI 메시지 변환 ---

    private List<Message> buildSpringAiMessages(ChatSession session) {
        return session.getMessages().stream()
                .<Message>map(m -> switch (m.role()) {
                    case "user" -> new UserMessage(m.content());
                    case "assistant" -> new AssistantMessage(m.content());
                    default -> new UserMessage(m.content());
                })
                .toList();
    }

    // --- Spring AI Tool Callbacks (buyerId, session 클로저로 캡처) ---

    private ToolCallback[] buildToolCallbacks(Long buyerId, ChatSession session) {
        return new ToolCallback[]{
                FunctionToolCallback.builder("searchProducts",
                                (SearchProductsRequest req) -> {
                                    ChatToolExecutor.SearchWithResults rich = toolExecutor.searchProductsRich(req.query());
                                    if (!rich.items().isEmpty()) {
                                        pendingProductLists.put(buyerId, rich.items());
                                    }
                                    return rich.toolText();
                                })
                        .description("재고 있는 상품을 자연어로 검색합니다. " +
                                "고객이 상품명을 말하거나 추천 요청 시 반드시 먼저 호출하세요. " +
                                "결과에는 productId와 단가가 포함되어 있어 주문에 바로 사용할 수 있습니다.")
                        .inputType(SearchProductsRequest.class)
                        .build(),
                FunctionToolCallback.builder("manageCart",
                                (ManageCartRequest req) -> {
                                    if ("remove".equalsIgnoreCase(req.action())) {
                                        session.removeFromCart(req.productId());
                                        log.debug("장바구니 제거: buyerId={}, productId={}", buyerId, req.productId());
                                        return "장바구니에서 제거됨: " + req.productName()
                                                + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
                                    }
                                    int qty = req.quantity() > 0 ? req.quantity() : 1;
                                    session.addOrUpdateCart(req.productId(), req.productName(), req.unitPrice(), qty);
                                    long subtotal = req.unitPrice() * qty;
                                    log.debug("장바구니 추가/변경: buyerId={}, product={}, qty={}", buyerId, req.productName(), qty);
                                    return "장바구니 업데이트: " + req.productName() + " " + qty + "개"
                                            + " (소계 " + String.format("%,d", subtotal) + "원)"
                                            + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
                                })
                        .description("고객이 상품을 선택하거나 수량을 변경하거나 취소할 때 반드시 호출해 장바구니를 갱신합니다. " +
                                "action: 'add'(추가/수량변경) 또는 'remove'(취소). " +
                                "productId, productName, unitPrice는 searchProducts 또는 getProductUnit 결과를 그대로 사용하세요.")
                        .inputType(ManageCartRequest.class)
                        .build(),
                FunctionToolCallback.builder("getProductUnit",
                                (GetProductUnitRequest req) -> toolExecutor.getProductUnit(req.productId()))
                        .description("특정 상품의 단위(박스, 송이, 팩 등)와 단위당 수량, 단가를 조회합니다. " +
                                "고객이 수량 단위를 명확히 하지 않았을 때 사용하세요.")
                        .inputType(GetProductUnitRequest.class)
                        .build(),
                FunctionToolCallback.builder("getRecentOrder",
                                (GetRecentOrderRequest req) -> toolExecutor.getRecentOrder(buyerId))
                        .description("현재 고객의 가장 최근 주문 내역(상품, 수량, 배송지)을 조회합니다. " +
                                "배송지 미저장 상태에서 수집 단계 진입 시 반드시 먼저 호출하세요. " +
                                "고객이 '저번이랑 똑같이', '이전 배송지로' 등 이전 주문을 참조할 때도 사용하세요.")
                        .inputType(GetRecentOrderRequest.class)
                        .build(),
                FunctionToolCallback.builder("saveDeliveryAddress",
                                (SaveDeliveryAddressRequest req) -> {
                                    session.saveDeliveryAddress(req.zipCode(), req.roadAddress(),
                                            req.detailAddress(), req.deliveryRequest());
                                    log.debug("배송지 저장: buyerId={}, address={} {}", buyerId, req.roadAddress(), req.detailAddress());
                                    // auth-service에도 기본 배송지로 저장 (다음 세션 자동 적재용, best-effort)
                                    authServiceClient.saveDefaultAddress(buyerId, req.zipCode(), req.roadAddress(), req.detailAddress());
                                    return "배송지 저장됨: " + req.zipCode() + " " + req.roadAddress()
                                            + (req.detailAddress() != null ? " " + req.detailAddress() : "");
                                })
                        .description("고객이 배송지를 입력하거나 이전 배송지를 사용하겠다고 확인하면 반드시 이 도구로 저장하세요. " +
                                "zipCode는 5자리 숫자 문자열, detailAddress/deliveryRequest가 없으면 null.")
                        .inputType(SaveDeliveryAddressRequest.class)
                        .build()
        };
    }

    // --- 상품 목록 SSE 이벤트 ---

    private Flux<ServerSentEvent<String>> handleProductList(Long buyerId) {
        List<ProductSearchResult> results = pendingProductLists.remove(buyerId);
        if (results == null || results.isEmpty()) return Flux.empty();
        try {
            String json = objectMapper.writeValueAsString(results);
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("product_list")
                    .data(json)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("product_list 직렬화 실패: buyerId={}", buyerId);
            return Flux.empty();
        }
    }

    // --- 주문 확정 처리 ---

    private Flux<ServerSentEvent<String>> handleOrderConfirm(String fullText, Long buyerId) {
        Optional<OrderConfirmData> confirmOpt = orderConfirmParser.parse(fullText);
        if (confirmOpt.isEmpty()) return Flux.empty();

        // ORDER_CONFIRM 감지 즉시 세션 리셋 — 이력에 ##ORDER_CONFIRM## 남으면 다음 턴에 중복 주문 위험
        sessionStore.remove(buyerId);
        log.info("주문 확정 감지, 세션 리셋: buyerId={}", buyerId);

        OrderConfirmData confirm = confirmOpt.get();
        return Mono.fromCallable(() -> orderServiceClient.createOrder(confirm, buyerId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ServerSentEvent.<String>builder()
                        .event("order_created")
                        .data(toJsonWithMeta(result, confirm))
                        .build())
                .onErrorResume(OrderCreateException.class, e -> {
                    // 4xx — 재고 부족, 가격 불일치 등 고객에게 원인 안내
                    log.warn("주문 생성 실패(클라이언트 오류): buyerId={}, status={}, message={}",
                            buyerId, e.getStatusCode(), e.getMessage());
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("text")
                            .data(e.getMessage())
                            .build());
                })
                .onErrorResume(e -> {
                    log.error("주문 생성 실패: buyerId={}, error={}", buyerId, e.getMessage());
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("주문 처리 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.")
                            .build());
                })
                .flux();
    }

    private String toJsonWithMeta(OrderCreateResult result, OrderConfirmData confirm) {
        try {
            List<OrderConfirmData.OrderLineData> lines = confirm.orderLines();
            String orderName = lines.isEmpty() ? "주문"
                    : lines.size() == 1
                        ? lines.get(0).productNameSnapshot()
                        : lines.get(0).productNameSnapshot() + " 외 " + (lines.size() - 1) + "건";

            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("orderId",     String.valueOf(result.orderId())); // string — Snowflake ID JS 정밀도 손실 방지
            node.put("tossOrderId", result.tossOrderId());
            node.put("amount",      result.amount());
            node.put("payMethod",   confirm.payMethod());
            node.put("orderName",   orderName);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"orderId\":\"" + result.orderId() + "\",\"tossOrderId\":\"" + result.tossOrderId()
                    + "\",\"amount\":" + result.amount() + ",\"payMethod\":\"" + confirm.payMethod() + "\"}";
        }
    }

    // --- Tool 입력 타입 (Spring AI가 JSON → Record 역직렬화) ---

    record SearchProductsRequest(@JsonProperty("query") String query) {}

    record ManageCartRequest(
            @JsonProperty("action")      String action,      // "add" | "remove"
            @JsonProperty("productId")   Long   productId,
            @JsonProperty("productName") String productName,
            @JsonProperty("unitPrice")   long   unitPrice,
            @JsonProperty("quantity")    int    quantity
    ) {}

    record GetProductUnitRequest(@JsonProperty("productId") Long productId) {}

    record GetRecentOrderRequest() {}

    record SaveDeliveryAddressRequest(
            @JsonProperty("zipCode")         String zipCode,
            @JsonProperty("roadAddress")     String roadAddress,
            @JsonProperty("detailAddress")   String detailAddress,
            @JsonProperty("deliveryRequest") String deliveryRequest
    ) {}
}
