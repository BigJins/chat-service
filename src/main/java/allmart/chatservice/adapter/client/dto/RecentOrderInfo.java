package allmart.chatservice.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * order-service GET /internal/orders/recent?buyerId={id} 응답 DTO.
 * OrderDetailResponse flat 구조 수신 (zipCode/roadAddress/detailAddress 최상위 필드).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecentOrderInfo(
        Long orderId,
        List<OrderLine> orderLines,
        String zipCode,
        String roadAddress,
        String detailAddress,
        String createdAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderLine(
            String productNameSnapshot,
            int quantity
    ) {}
}