package allmart.chatservice.adapter.client.dto;

/**
 * @deprecated AuthServiceClient 내부 SavedAddressResponse record로 대체됨.
 *             AddressPort.getDefaultAddress() 가 DeliveryAddress를 직접 반환.
 */
@Deprecated
public record SavedAddressInfo(
        String id, String zipCode, String roadAddress,
        String detailAddress, String label, boolean isDefault) {}
