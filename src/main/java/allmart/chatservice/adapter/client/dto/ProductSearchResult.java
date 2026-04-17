package allmart.chatservice.adapter.client.dto;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * search-service GET /internal/search/products/rag 응답 DTO.
 * productId: JS Number.MAX_SAFE_INTEGER 초과 Snowflake ID 정밀도 손실 방지 → string 직렬화
 */
public record ProductSearchResult(
        @JsonSerialize(using = ToStringSerializer.class)
        Long productId,
        String productName,
        String categoryName,
        long sellingPrice,
        boolean inStock,
        String unit,
        Integer unitSize,
        String status,
        String imageUrl
) {}
