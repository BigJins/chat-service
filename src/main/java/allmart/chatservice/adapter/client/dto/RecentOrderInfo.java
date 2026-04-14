package allmart.chatservice.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * order-query-service GET /api/orders/recent?buyerId={id} 응답 DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecentOrderInfo(
        String orderId,
        List<OrderLine> orderLines,
        DeliverySnapshot deliverySnapshot,
        String createdAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderLine(
            String productNameSnapshot,
            int quantity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliverySnapshot(
            String zipCode,
            String roadAddress,
            String detailAddress
    ) {}
}
