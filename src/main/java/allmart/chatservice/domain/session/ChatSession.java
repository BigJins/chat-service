package allmart.chatservice.domain.session;

import lombok.Getter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * buyerId 기준 대화 세션.
 * 메시지 이력은 Spring AI InMemoryChatMemory가 관리.
 * 세션은 cartItems·deliveryAddress·chatMemory 보유.
 * Guava Cache expireAfterAccess 30분 — 만료 시 chatMemory 포함 전체 소멸.
 */
@Getter
public class ChatSession {

    /** MessageChatMemoryAdvisor에 넘길 대화 ID. 세션 = 1 유저 = 상수 사용 */
    public static final String CONVERSATION_ID = "main";

    private final Long buyerId;

    /** Spring AI 대화 이력 저장소 — 슬라이딩 윈도우 20메시지 (10턴) */
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(20)
            .build();

    private final List<CartItem> cartItems = new ArrayList<>();
    private DeliveryAddress deliveryAddress;

    /** 세션 시작 시 기본 배송지 사전 적재 여부 (최초 1회만 auth-service 호출) */
    private boolean addressPreloaded = false;

    public ChatSession(Long buyerId) {
        this.buyerId = buyerId;
    }

    // ── 장바구니 ──────────────────────────────────────────────

    public synchronized void addOrUpdateCart(Long productId, String productName,
                                              long unitPrice, int quantity) {
        cartItems.removeIf(item -> item.productId().equals(productId));
        if (quantity > 0) {
            cartItems.add(new CartItem(productId, productName, unitPrice, quantity));
        }
    }

    public synchronized void removeFromCart(Long productId) {
        cartItems.removeIf(item -> item.productId().equals(productId));
    }

    public synchronized List<CartItem> getCartItems() {
        return Collections.unmodifiableList(cartItems);
    }

    public synchronized long cartTotal() {
        return cartItems.stream().mapToLong(CartItem::subtotal).sum();
    }

    // ── 배송지 ────────────────────────────────────────────────

    public synchronized void saveDeliveryAddress(String zipCode, String roadAddress,
                                                  String detailAddress, String deliveryRequest) {
        this.deliveryAddress = new DeliveryAddress(zipCode, roadAddress, detailAddress, deliveryRequest);
    }

    public synchronized DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    // ── 플래그 ────────────────────────────────────────────────

    public boolean isAddressPreloaded() {
        return addressPreloaded;
    }

    public synchronized void markAddressPreloaded() {
        this.addressPreloaded = true;
    }

    public synchronized void clear() {
        cartItems.clear();
        deliveryAddress = null;
        addressPreloaded = false;
        chatMemory.clear(CONVERSATION_ID);
    }
}
