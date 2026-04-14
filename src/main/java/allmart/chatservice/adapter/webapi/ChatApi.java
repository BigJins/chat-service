package allmart.chatservice.adapter.webapi;

import allmart.chatservice.adapter.webapi.dto.ChatRequest;
import allmart.chatservice.application.ChatUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatApi {

    private final ChatUseCase chatUseCase;

    /**
     * 채팅 메시지 전송 → SSE 스트리밍 응답.
     *
     * SSE 이벤트 타입:
     *   event: text         — Claude 텍스트 청크
     *   event: order_created — 주문 완료 {"orderId":1,"tossOrderId":"...","amount":30000}
     *   event: error        — 오류 메시지
     *
     * 클라이언트(Vue 3)에서 fetch() + ReadableStream으로 소비 권장
     * (EventSource는 POST를 지원하지 않음)
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestBody @Valid ChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {

        Long buyerId = buyerIdFromGateway != null ? buyerIdFromGateway : request.buyerId();
        log.debug("채팅 요청: buyerId={}, messageLen={}", buyerId, request.message().length());

        return chatUseCase.stream(buyerId, request.message());
    }

    /**
     * 대화 세션 초기화.
     * 새 주문 시작 또는 주문 완료 후 세션 리셋.
     */
    @DeleteMapping("/api/chat/sessions/{buyerId}")
    public void resetSession(@PathVariable Long buyerId) {
        chatUseCase.resetSession(buyerId);
    }
}
