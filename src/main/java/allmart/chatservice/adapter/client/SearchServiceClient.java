package allmart.chatservice.adapter.client;

import allmart.chatservice.adapter.client.dto.ProductSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * search-service 내부 API 클라이언트.
 * GET /internal/search/products/rag — RAG kNN 벡터 검색
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchServiceClient {

    @Qualifier("searchServiceRestClient")
    private final RestClient restClient;

    /**
     * 자연어 쿼리로 상품 벡터 검색 (kNN).
     * inStock=true 항상 적용.
     *
     * @param query 자연어 검색어 ("달달한 간식", "비타민 풍부한 과일" 등)
     * @param size  반환 개수
     */
    public List<ProductSearchResult> ragSearch(String query, int size) {
        log.debug("RAG 상품 검색 요청: query={}, size={}", query, size);
        List<ProductSearchResult> results = restClient.get()
                .uri("/internal/search/products/rag?q={q}&size={size}", query, size)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        log.debug("RAG 상품 검색 완료: query={}, 결과={}", query, results != null ? results.size() : 0);
        return results != null ? results : List.of();
    }
}
