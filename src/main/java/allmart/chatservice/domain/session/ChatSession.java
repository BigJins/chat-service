package allmart.chatservice.domain.session;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * buyerId 기준 대화 세션.
 * 슬라이딩 윈도우 10턴(user+assistant 각 10개씩 최대 20개)으로 토큰 비용 제어.
 * cartItems는 슬라이딩 윈도우와 무관하게 세션 전체에서 유지됨.
 */
@Getter
public class ChatSession {

    private static final int MAX_TURNS = 10; // user+assistant 쌍 기준

    private final Long buyerId;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    private DeliveryAddress deliveryAddress;

    public ChatSession(Long buyerId) {
        this.buyerId = buyerId;
    }

    public synchronized void addUserMessage(String content) {
        messages.add(ChatMessage.user(content));
        trimIfNeeded();
    }

    public synchronized void addAssistantMessage(String content) {
        messages.add(ChatMessage.assistant(content));
        trimIfNeeded();
    }

    /** 현재 대화 이력 (불변 뷰) */
    public synchronized List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    // ── 장바구니 ───────────────────────────────────────────────

    /** 상품 추가 또는 수량 갱신. quantity <= 0 이면 제거. */
    public synchronized void addOrUpdateCart(Long productId, String productName, long unitPrice, int quantity) {
        cartItems.removeIf(item -> item.productId().equals(productId));
        if (quantity > 0) {
            cartItems.add(new CartItem(productId, productName, unitPrice, quantity));
        }
    }

    public synchronized void removeFromCart(Long productId) {
        cartItems.removeIf(item -> item.productId().equals(productId));
    }

    /** 현재 장바구니 (불변 뷰) */
    public synchronized List<CartItem> getCartItems() {
        return Collections.unmodifiableList(cartItems);
    }

    public synchronized long cartTotal() {
        return cartItems.stream().mapToLong(CartItem::subtotal).sum();
    }

    // ── 배송지 ─────────────────────────────────────────────────

    public synchronized void saveDeliveryAddress(String zipCode, String roadAddress,
                                                  String detailAddress, String deliveryRequest) {
        this.deliveryAddress = new DeliveryAddress(zipCode, roadAddress, detailAddress, deliveryRequest);
    }

    public synchronized DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    // ──────────────────────────────────────────────────────────

    public synchronized void clear() {
        messages.clear();
        cartItems.clear();
        deliveryAddress = null;
    }

    /** 슬라이딩 윈도우: MAX_TURNS * 2(user+assistant) 초과 시 앞에서 제거 */
    private void trimIfNeeded() {
        int maxMessages = MAX_TURNS * 2;
        while (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }
}
