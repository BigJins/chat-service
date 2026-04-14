package allmart.chatservice.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * ##ORDER_CONFIRM## 마커 뒤에 오는 JSON 파싱 결과.
 * Claude가 출력한 주문 확정 데이터.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderConfirmData(
        Long martId,
        String martName,
        List<OrderLineData> orderLines,
        String payMethod,
        String zipCode,
        String roadAddress,
        String detailAddress,
        String deliveryRequest
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderLineData(
            Long productId,
            String productNameSnapshot,
            Long unitPrice,
            Integer quantity
    ) {}
}
