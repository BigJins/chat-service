package allmart.chatservice.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * product-service GET /internal/products/{id}/price 응답 DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductUnitInfo(
        Long productId,
        String name,
        long price,
        String taxType,
        String unit,
        Integer unitSize
) {}
