package allmart.chatservice.application.provided;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 채팅 인바운드 포트 (Provided Port).
 * adapter/webapi/ChatApi → ChatService 구현체.
 */
public interface ChatUseCase {

    /**
     * 채팅 메시지 처리 → SSE 스트림 응답.
     *
     * SSE 이벤트 타입:
     *   text          — Claude 텍스트 청크 (스트리밍)
     *   product_list  — 상품 카드 목록 JSON 배열
     *   order_created — 주문 완료 {"orderId":"1","tossOrderId":"...","amount":30000,"payMethod":"CARD","orderName":"..."}
     *   error         — 오류 메시지
     */
    Flux<ServerSentEvent<String>> stream(Long buyerId, String message);

    /** 세션 초기화 */
    void resetSession(Long buyerId);
}
