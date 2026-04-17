package allmart.chatservice.adapter.client;

import allmart.chatservice.adapter.client.dto.OrderConfirmData;
import allmart.chatservice.adapter.client.dto.OrderCreateResult;
import allmart.chatservice.adapter.client.dto.RecentOrderInfo;
import allmart.chatservice.application.required.OrderCreationPort;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * order-service 호출 클라이언트.
 * - POST /api/orders — 주문 생성
 * - GET /internal/orders/recent — 최근 주문 조회 (배송지 재사용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderServiceClient implements OrderCreationPort {

    @Qualifier("orderServiceRestClient")
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OrderCreateResult createOrder(OrderConfirmData confirm, Long buyerId) {
        ObjectNode body = buildOrderBody(confirm, buyerId);
        try {
            OrderCreateResult result = restClient.post()
                    .uri("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OrderCreateResult.class);
            log.info("주문 생성 완료: orderId={}, tossOrderId={}",
                    result != null ? result.orderId() : null,
                    result != null ? result.tossOrderId() : null);
            return result;
        } catch (RestClientResponseException e) {
            String friendlyMessage = extractMessage(e);
            log.warn("주문 생성 실패: status={}, message={}", e.getStatusCode().value(), friendlyMessage);
            throw new OrderCreateException(friendlyMessage, e.getStatusCode().value());
        }
    }

    public RecentOrderInfo getRecentOrder(Long buyerId) {
        log.debug("최근 주문 조회 요청: buyerId={}", buyerId);
        return restClient.get()
                .uri("/internal/orders/recent?buyerId={buyerId}", buyerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) ->
                        log.debug("최근 주문 없음({}): buyerId={}", res.getStatusCode().value(), buyerId))
                .body(RecentOrderInfo.class);
    }

    /** order-service GlobalExceptionHandler 응답에서 message 필드 추출 */
    private String extractMessage(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            tools.jackson.databind.JsonNode node = objectMapper.readTree(body);
            if (node.has("message")) return node.get("message").asText();
        } catch (Exception ignored) {
            // 파싱 실패 시 상태 코드 기반 기본 메시지 사용
        }
        return e.getStatusCode().value() == 400
                ? "주문 처리 중 문제가 발생했어요. 상품 재고나 가격을 확인해 주세요."
                : "주문 처리 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.";
    }

    private ObjectNode buildOrderBody(OrderConfirmData confirm, Long buyerId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("buyerId", buyerId);
        body.put("payMethod", confirm.payMethod());

        // orderLines
        ArrayNode lines = body.putArray("orderLines");
        for (OrderConfirmData.OrderLineData line : confirm.orderLines()) {
            ObjectNode lineNode = lines.addObject();
            lineNode.put("productId", line.productId());
            lineNode.put("productNameSnapshot", line.productNameSnapshot());
            lineNode.put("unitPrice", line.unitPrice());
            lineNode.put("quantity", line.quantity());
            lineNode.putNull("taxType");
        }

        // deliverySnapshot
        ObjectNode delivery = body.putObject("deliverySnapshot");
        delivery.put("zipCode", confirm.zipCode());
        delivery.put("roadAddress", confirm.roadAddress());
        // detailAddress null → 빈 문자열 (DeliverySnapshot 도메인 규칙 준수)
        delivery.put("detailAddress", confirm.detailAddress() != null ? confirm.detailAddress() : "");

        // martSnapshot
        ObjectNode mart = body.putObject("martSnapshot");
        mart.put("martId", confirm.martId());
        mart.put("martName", confirm.martName());
        mart.putNull("martPhone");

        // orderMemo (배달 요청사항)
        if (confirm.deliveryRequest() != null) {
            ObjectNode memo = body.putObject("orderMemo");
            memo.putNull("orderRequest");
            memo.put("deliveryRequest", confirm.deliveryRequest());
        } else {
            body.putNull("orderMemo");
        }

        return body;
    }
}
