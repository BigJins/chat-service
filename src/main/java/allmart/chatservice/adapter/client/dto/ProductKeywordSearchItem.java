package allmart.chatservice.adapter.client.dto;

/**
 * product-service GET /internal/products/search 응답 DTO.
 * ProductIndexResponse 필드와 일치.
 */
public record ProductKeywordSearchItem(
        Long productId,
        String name,
        String categoryName,
        long sellingPrice,
        String unit,
        Integer unitSize,
        String status,
        String imageUrl
) {
    public ProductSearchResult toSearchResult() {
        boolean inStock = "ACTIVE".equals(status);
        return new ProductSearchResult(productId, name, categoryName, sellingPrice, inStock, unit, unitSize, status, imageUrl);
    }
}
