package allmart.chatservice.adapter.mcp;

import allmart.chatservice.application.tool.ChatToolExecutor;
import allmart.chatservice.domain.session.CartItem;
import allmart.chatservice.domain.session.ChatSession;
import allmart.chatservice.domain.session.ChatSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 서버 전용 툴 어댑터 — Spring 싱글턴 Bean.
 *
 * SSE 흐름의 ChatTools(non-bean, per-request, buyerId+session 생성자 주입)와 달리
 * buyerId를 각 툴 파라미터로 받아 ChatSessionStore에서 세션을 lazy 조회.
 *
 * 비즈니스 로직은 ChatToolExecutor에 완전히 위임하여 중복 없음.
 * SSE 흐름과 MCP 흐름이 동일한 ChatSessionStore(Guava Cache)를 공유
 * → 두 채널에서 동일한 buyerId로 접근 시 장바구니 상태 일관성 보장.
 *
 * 노출 엔드포인트 (WebFlux SSE 트랜스포트):
 *   GET  /mcp/sse      — MCP 클라이언트 연결
 *   POST /mcp/message  — 툴 호출 메시지 수신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllmartMcpTools {

    private final ChatToolExecutor toolExecutor;
    private final ChatSessionStore sessionStore;

    @Tool(description = "재고 있는 상품을 자연어로 검색합니다. " +
            "고객이 상품명을 말하거나 추천 요청 시 반드시 먼저 호출하세요. " +
            "결과에는 productId와 단가가 포함되어 있어 주문에 바로 사용할 수 있습니다.")
    public String searchProducts(String query) {
        log.debug("MCP searchProducts: query={}", query);
        return toolExecutor.searchProducts(query);
    }

    @Tool(description = "고객이 상품을 선택하거나 수량을 변경하거나 취소할 때 장바구니를 갱신합니다. " +
            "action: 'add'(추가/수량변경) 또는 'remove'(삭제). " +
            "productId, productName, unitPrice는 searchProducts 또는 getProductUnit 결과를 사용하세요.")
    public String manageCart(Long buyerId, String action, Long productId,
                              String productName, long unitPrice, int quantity) {
        ChatSession session = sessionStore.getOrCreate(buyerId);
        if ("remove".equalsIgnoreCase(action)) {
            session.removeFromCart(productId);
            log.debug("MCP 장바구니 제거: buyerId={}, productId={}", buyerId, productId);
            return "장바구니에서 제거됨: " + productName
                    + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
        }
        int qty = quantity > 0 ? quantity : 1;
        session.addOrUpdateCart(productId, productName, unitPrice, qty);
        long subtotal = unitPrice * qty;
        log.debug("MCP 장바구니 추가/변경: buyerId={}, product={}, qty={}", buyerId, productName, qty);
        return "장바구니 업데이트: " + productName + " " + qty + "개"
                + " (소계 " + String.format("%,d", subtotal) + "원)"
                + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
    }

    @Tool(description = "특정 상품의 단위(박스, 송이, 팩 등)와 단위당 수량, 단가를 조회합니다. " +
            "고객이 수량 단위를 명확히 하지 않았을 때 사용하세요.")
    public String getProductUnit(Long productId) {
        log.debug("MCP getProductUnit: productId={}", productId);
        return toolExecutor.getProductUnit(productId);
    }

    @Tool(description = "고객의 가장 최근 주문 내역(상품, 수량, 배송지)을 조회합니다. " +
            "배송지 재입력 없이 이전 주소를 재사용하거나 이전 주문을 그대로 반복할 때 사용하세요.")
    public String getRecentOrder(Long buyerId) {
        log.debug("MCP getRecentOrder: buyerId={}", buyerId);
        return toolExecutor.getRecentOrder(buyerId);
    }

    @Tool(description = "배송지를 세션에 저장합니다. " +
            "zipCode는 5자리 숫자 문자열. detailAddress, deliveryRequest가 없으면 null.")
    public String saveDeliveryAddress(Long buyerId, String zipCode, String roadAddress,
                                       String detailAddress, String deliveryRequest) {
        ChatSession session = sessionStore.getOrCreate(buyerId);
        session.saveDeliveryAddress(zipCode, roadAddress, detailAddress, deliveryRequest);
        log.debug("MCP 배송지 저장: buyerId={}, address={}", buyerId, roadAddress);
        return "배송지 저장됨: " + zipCode + " " + roadAddress
                + (detailAddress != null ? " " + detailAddress : "");
    }

    @Tool(description = "현재 장바구니에 담긴 상품 목록과 합계를 조회합니다. " +
            "주문 전 고객이 담은 상품을 확인하거나 최종 확인 단계에서 사용하세요.")
    public String getCart(Long buyerId) {
        ChatSession session = sessionStore.getOrCreate(buyerId);
        List<CartItem> items = session.getCartItems();
        if (items.isEmpty()) return "장바구니가 비어있습니다.";
        StringBuilder sb = new StringBuilder("현재 장바구니:\n");
        for (CartItem item : items) {
            sb.append("- ").append(item.productName())
                    .append(" ").append(item.quantity()).append("개")
                    .append(" (소계 ").append(String.format("%,d", item.subtotal())).append("원)\n");
        }
        sb.append("합계: ").append(String.format("%,d", session.cartTotal())).append("원");
        return sb.toString();
    }
}
