package allmart.chatservice.application.tool;

import allmart.chatservice.application.required.AddressPort;
import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import allmart.chatservice.domain.session.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

/**
 * Spring AI 2.0 @Tool 기반 도구 모음 — 요청 단위 인스턴스.
 * Spring 빈이 아닌 일반 클래스. ChatService에서 요청마다 new ChatTools(...) 생성.
 * buyerId·session은 생성자 주입으로 클로저 대체.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatTools {

    private final Long buyerId;
    private final ChatSession session;
    private final ChatToolExecutor toolExecutor;
    private final AddressPort addressPort;
    private final Map<Long, List<ProductSearchResult>> pendingProductLists;

    @Tool(description = "재고 있는 상품을 자연어로 검색합니다. " +
            "고객이 상품명을 말하거나 추천 요청 시 반드시 먼저 호출하세요. " +
            "결과에는 productId와 단가가 포함되어 있어 주문에 바로 사용할 수 있습니다.")
    public String searchProducts(String query) {
        ChatToolExecutor.SearchWithResults rich = toolExecutor.searchProductsRich(query);
        if (!rich.items().isEmpty()) {
            pendingProductLists.put(buyerId, rich.items());
        }
        return rich.toolText();
    }

    @Tool(description = "고객이 상품을 선택하거나 수량을 변경하거나 취소할 때 반드시 호출해 장바구니를 갱신합니다. " +
            "action: 'add'(추가/수량변경) 또는 'remove'(취소). " +
            "productId, productName, unitPrice는 searchProducts 또는 getProductUnit 결과를 그대로 사용하세요.")
    public String manageCart(String action, Long productId, String productName,
                              long unitPrice, int quantity) {
        if ("remove".equalsIgnoreCase(action)) {
            session.removeFromCart(productId);
            log.debug("장바구니 제거: buyerId={}, productId={}", buyerId, productId);
            return "장바구니에서 제거됨: " + productName
                    + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
        }
        int qty = quantity > 0 ? quantity : 1;
        session.addOrUpdateCart(productId, productName, unitPrice, qty);
        long subtotal = unitPrice * qty;
        log.debug("장바구니 추가/변경: buyerId={}, product={}, qty={}", buyerId, productName, qty);
        return "장바구니 업데이트: " + productName + " " + qty + "개"
                + " (소계 " + String.format("%,d", subtotal) + "원)"
                + ", 현재 합계 " + String.format("%,d", session.cartTotal()) + "원";
    }

    @Tool(description = "특정 상품의 단위(박스, 송이, 팩 등)와 단위당 수량, 단가를 조회합니다. " +
            "고객이 수량 단위를 명확히 하지 않았을 때 사용하세요.")
    public String getProductUnit(Long productId) {
        return toolExecutor.getProductUnit(productId);
    }

    @Tool(description = "현재 고객의 가장 최근 주문 내역(상품, 수량, 배송지)을 조회합니다. " +
            "배송지 미저장 상태에서 수집 단계 진입 시 반드시 먼저 호출하세요. " +
            "고객이 '저번이랑 똑같이', '이전 배송지로' 등 이전 주문을 참조할 때도 사용하세요.")
    public String getRecentOrder() {
        return toolExecutor.getRecentOrder(buyerId);
    }

    @Tool(description = "고객이 배송지를 입력하거나 이전 배송지를 사용하겠다고 확인하면 반드시 이 도구로 저장하세요. " +
            "zipCode는 5자리 숫자 문자열, detailAddress/deliveryRequest가 없으면 null.")
    public String saveDeliveryAddress(String zipCode, String roadAddress,
                                       String detailAddress, String deliveryRequest) {
        session.saveDeliveryAddress(zipCode, roadAddress, detailAddress, deliveryRequest);
        log.debug("배송지 저장: buyerId={}, address={} {}", buyerId, roadAddress, detailAddress);

        // auth-service 저장은 best-effort (다음 세션 자동 적재용) — fire-and-forget.
        // 결과가 현재 응답에 불필요하므로 동기 대기 없이 별도 VT에서 실행.
        Thread.ofVirtual().start(() ->
                addressPort.saveDefaultAddress(buyerId, zipCode, roadAddress, detailAddress));

        return "배송지 저장됨: " + zipCode + " " + roadAddress
                + (detailAddress != null ? " " + detailAddress : "");
    }
}
