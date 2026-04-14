package allmart.chatservice.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * order-service POST /api/orders 응답 DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreateResult(Long orderId, String tossOrderId, long amount) {
}
