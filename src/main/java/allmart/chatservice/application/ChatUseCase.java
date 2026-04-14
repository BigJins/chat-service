package allmart.chatservice.application;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatUseCase {

    /**
     * 채팅 메시지를 처리하고 SSE 스트림으로 응답.
     *
     * SSE 이벤트 타입:
     *   text         — Claude 텍스트 청크 (스트리밍)
     *   order_created — 주문 생성 완료 {"orderId":1,"tossOrderId":"...","amount":30000}
     *   error        — 오류 메시지
     */
    Flux<ServerSentEvent<String>> stream(Long buyerId, String message);

    /** 세션 초기화 */
    void resetSession(Long buyerId);
}
