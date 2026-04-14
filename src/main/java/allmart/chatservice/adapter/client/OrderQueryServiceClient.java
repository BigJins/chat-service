package allmart.chatservice.adapter.client;

import allmart.chatservice.adapter.client.dto.RecentOrderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * order-query-service API 클라이언트.
 * GET /api/orders/recent?buyerId={id} — 최근 주문 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryServiceClient {

    @Qualifier("orderQueryServiceRestClient")
    private final RestClient restClient;

    public RecentOrderInfo getRecentOrder(Long buyerId) {
        log.debug("최근 주문 조회 요청: buyerId={}", buyerId);
        RecentOrderInfo info = restClient.get()
                .uri("/api/orders/recent?buyerId={buyerId}", buyerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // 404 = 첫 주문 고객, null 반환으로 처리
                    log.debug("최근 주문 없음({}): buyerId={}", res.getStatusCode().value(), buyerId);
                })
                .body(RecentOrderInfo.class);
        log.debug("최근 주문 조회 완료: buyerId={}, orderId={}", buyerId, info != null ? info.orderId() : null);
        return info;
    }
}
