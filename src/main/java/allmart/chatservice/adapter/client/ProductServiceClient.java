package allmart.chatservice.adapter.client;

import allmart.chatservice.adapter.client.dto.ProductKeywordSearchItem;
import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import allmart.chatservice.adapter.client.dto.ProductUnitInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * product-service 내부 API 클라이언트.
 * GET /internal/products/{id}/price — 상품 단위/가격 정보 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    @Qualifier("productServiceRestClient")
    private final RestClient restClient;

    public ProductUnitInfo getProductUnit(Long productId) {
        log.debug("상품 단위 조회 요청: productId={}", productId);
        ProductUnitInfo info = restClient.get()
                .uri("/internal/products/{id}/price", productId)
                .retrieve()
                .body(ProductUnitInfo.class);
        log.debug("상품 단위 조회 완료: productId={}, name={}", productId, info != null ? info.name() : null);
        return info;
    }

    /**
     * 키워드 검색 — RAG 폴백용
     * product-service GET /internal/products/search?keyword=...&size=...
     */
    public List<ProductSearchResult> searchByKeyword(String keyword, int size) {
        log.debug("키워드 검색 요청: keyword={}, size={}", keyword, size);
        List<ProductKeywordSearchItem> items = restClient.get()
                .uri(u -> u.path("/internal/products/search")
                        .queryParam("keyword", keyword)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (items == null) return List.of();
        log.debug("키워드 검색 완료: keyword={}, count={}", keyword, items.size());
        return items.stream().map(ProductKeywordSearchItem::toSearchResult).toList();
    }
}
