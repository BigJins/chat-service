package allmart.chatservice.adapter.client;

/**
 * order-service 주문 생성 실패 (4xx) 시 발생.
 * message에 고객에게 보여줄 수 있는 원인이 담겨 있음.
 */
public class OrderCreateException extends RuntimeException {

    private final int statusCode;

    public OrderCreateException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
