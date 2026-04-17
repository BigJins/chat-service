package allmart.chatservice.application.required;

import allmart.chatservice.adapter.client.dto.OrderConfirmData;
import allmart.chatservice.adapter.client.dto.OrderCreateResult;

/**
 * 주문 생성 아웃바운드 포트 (Required Port).
 * adapter/client/OrderServiceClient 구현.
 */
public interface OrderCreationPort {
    OrderCreateResult createOrder(OrderConfirmData confirm, Long buyerId);
}
