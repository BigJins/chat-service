package allmart.chatservice.adapter.client.dto;

/**
 * auth-service GET /auth/customers/addresses 응답 DTO.
 * auth-service SavedAddressResponse와 필드 일치.
 */
public record SavedAddressInfo(
        Long id,
        String zipCode,
        String roadAddress,
        String detailAddress,
        String label,
        boolean isDefault
) {}