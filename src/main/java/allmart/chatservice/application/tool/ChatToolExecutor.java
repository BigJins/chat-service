package allmart.chatservice.application.tool;

import allmart.chatservice.adapter.client.OrderQueryServiceClient;
import allmart.chatservice.adapter.client.ProductServiceClient;
import allmart.chatservice.adapter.client.SearchServiceClient;
import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import allmart.chatservice.adapter.client.dto.ProductUnitInfo;
import allmart.chatservice.adapter.client.dto.RecentOrderInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI Tool Calling 도구 실행기.
 * FunctionCallback 람다에서 buyerId 클로저로 호출됨.
 * RestClient 기반 동기 호출 — Spring AI가 boundedElastic 스레드에서 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatToolExecutor {

    private final ProductServiceClient productServiceClient;
    private final OrderQueryServiceClient orderQueryServiceClient;
    private final SearchServiceClient searchServiceClient;

    /** 상품 단위/가격 조회 → Claude tool_result 문자열 반환 */
    public String getProductUnit(Long productId) {
        try {
            ProductUnitInfo info = productServiceClient.getProductUnit(productId);
            if (info == null) return "상품 단위 정보를 조회할 수 없습니다. (productId=" + productId + ")";

            String unitDesc = (info.unit() != null && info.unitSize() != null)
                    ? info.unit() + " 단위, 1" + info.unit() + "=" + info.unitSize() + "개"
                    : (info.unit() != null ? info.unit() + " 단위" : "단위 정보 없음");
            return unitDesc + ", 단가 " + String.format("%,d", info.price()) + "원";
        } catch (Exception e) {
            log.warn("상품 단위 조회 실패: productId={}, reason={}", productId, e.getMessage());
            return "상품 단위 정보를 조회할 수 없습니다. (productId=" + productId + ")";
        }
    }

    /**
     * 최근 주문 조회 → Claude tool_result 문자열 반환.
     * order-query-service(MongoDB)에서 조회.
     */
    public String getRecentOrder(Long buyerId) {
        try {
            RecentOrderInfo info = orderQueryServiceClient.getRecentOrder(buyerId);
            return formatRecentOrder(info);
        } catch (Exception e) {
            log.warn("최근 주문 조회 실패: buyerId={}, reason={}", buyerId, e.getMessage());
            return "최근 주문 내역을 조회할 수 없습니다.";
        }
    }

    /** 자연어 쿼리로 재고 있는 상품 검색 → Claude tool_result 문자열 반환 */
    public String searchProducts(String query) {
        return searchProductsRich(query).toolText();
    }

    /**
     * searchProducts + 구조화 데이터 동시 반환 (프론트 카드 UI용).
     * RAG(벡터) 검색 우선, 결과 없으면 product-service 키워드 검색으로 폴백.
     * 최대 5개 노출.
     */
    public SearchWithResults searchProductsRich(String query) {
        try {
            List<ProductSearchResult> results = searchServiceClient.ragSearch(query, 5);
            if (!results.isEmpty()) return new SearchWithResults(formatSearchResults(results), results);
            log.debug("RAG 검색 결과 없음, product-service 폴백: query={}", query);
        } catch (Exception e) {
            log.warn("RAG 검색 실패, product-service 폴백: query={}, reason={}", query, e.getMessage());
        }

        try {
            List<ProductSearchResult> fallback = productServiceClient.searchByKeyword(query, 8);
            if (fallback.isEmpty())
                return new SearchWithResults("'" + query + "' 관련 재고 있는 상품이 없습니다.", List.of());
            List<ProductSearchResult> capped = fallback.size() > 5 ? fallback.subList(0, 5) : fallback;
            return new SearchWithResults(formatSearchResults(capped), capped);
        } catch (Exception e) {
            log.warn("product-service 폴백도 실패: query={}, reason={}", query, e.getMessage());
            return new SearchWithResults("상품 검색 중 오류가 발생했습니다. 직접 상품명을 말씀해 주세요.", List.of());
        }
    }

    public record SearchWithResults(String toolText, List<ProductSearchResult> items) {}

    private String formatSearchResults(List<ProductSearchResult> results) {
        StringBuilder sb = new StringBuilder("검색 결과 (").append(results.size()).append("개):\n");
        for (int i = 0; i < results.size(); i++) {
            ProductSearchResult r = results.get(i);
            sb.append(i + 1).append(". ").append(r.productName())
                    .append(" (productId:").append(r.productId()).append(")")
                    .append(", 단가 ").append(String.format("%,d", r.sellingPrice())).append("원");
            if (r.unit() != null) {
                sb.append("/").append(r.unit());
                if (r.unitSize() != null) sb.append("(").append(r.unitSize()).append("개)");
            }
            if (i < results.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /** 최근 주문 정보 → Claude tool_result 문자열. 배송지 + 상품 이력 포함 */
    private String formatRecentOrder(RecentOrderInfo info) {
        if (info == null || info.deliverySnapshot() == null
                || info.deliverySnapshot().roadAddress() == null
                || info.deliverySnapshot().roadAddress().isBlank()) {
            return "이전 주문 없음(첫 주문). 고객에게 우편번호·도로명주소·상세주소를 순서대로 입력받으세요.";
        }
        RecentOrderInfo.DeliverySnapshot addr = info.deliverySnapshot();
        StringBuilder sb = new StringBuilder("이전 배송지: ").append(addr.roadAddress());
        if (addr.detailAddress() != null && !addr.detailAddress().isBlank()) {
            sb.append(" ").append(addr.detailAddress());
        }
        sb.append(" (").append(addr.zipCode()).append(")");

        if (info.orderLines() != null && !info.orderLines().isEmpty()) {
            sb.append("\n이전 주문 상품: ");
            for (int i = 0; i < info.orderLines().size(); i++) {
                RecentOrderInfo.OrderLine line = info.orderLines().get(i);
                if (i > 0) sb.append(", ");
                sb.append(line.productNameSnapshot()).append(" ").append(line.quantity()).append("개");
            }
        }
        return sb.toString();
    }
}