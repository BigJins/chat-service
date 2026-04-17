package allmart.chatservice.application.required;

import allmart.chatservice.domain.session.DeliveryAddress;

import java.util.Optional;

/**
 * 배송지 아웃바운드 포트 (Required Port).
 * adapter/client/AuthServiceClient 구현.
 *
 * getDefaultAddress — 기본 배송지 조회. 없거나 실패 시 Optional.empty().
 * saveDefaultAddress — 배송지 저장 (best-effort, 실패 시 무시).
 */
public interface AddressPort {
    Optional<DeliveryAddress> getDefaultAddress(Long buyerId);
    void saveDefaultAddress(Long buyerId, String zipCode, String roadAddress, String detailAddress);
}
