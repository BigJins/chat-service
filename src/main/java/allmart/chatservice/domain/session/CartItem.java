package allmart.chatservice.domain.session;

/**
 * 장바구니 항목 — ChatSession에 명시적으로 저장.
 * LLM 대화 이력이 아닌 구조체로 관리해 슬라이딩 윈도우 trimming 시에도 유실되지 않음.
 */
public record CartItem(Long productId, String productName, long unitPrice, int quantity) {

    public long subtotal() {
        return unitPrice * quantity;
    }
}
