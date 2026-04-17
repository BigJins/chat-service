package allmart.chatservice.adapter.client;

import allmart.chatservice.application.required.AddressPort;
import allmart.chatservice.domain.session.DeliveryAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * auth-service API 클라이언트. AddressPort 구현.
 * GET  /auth/customers/addresses — 기본 배송지 조회
 * POST /auth/customers/addresses — 배송지 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthServiceClient implements AddressPort {

    @Qualifier("authServiceRestClient")
    private final RestClient restClient;

    /**
     * isDefault=true 배송지를 DeliveryAddress로 변환해 반환.
     * 없거나 API 실패 시 Optional.empty().
     */
    @Override
    public Optional<DeliveryAddress> getDefaultAddress(Long buyerId) {
        try {
            List<SavedAddressResponse> addresses = restClient.get()
                    .uri("/auth/customers/addresses")
                    .header("X-User-Id", buyerId.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (addresses == null) return Optional.empty();

            return addresses.stream()
                    .filter(SavedAddressResponse::isDefault)
                    .filter(a -> a.roadAddress() != null && !a.roadAddress().isBlank())
                    .findFirst()
                    .map(a -> new DeliveryAddress(a.zipCode(), a.roadAddress(), a.detailAddress(), null));
        } catch (Exception e) {
            log.warn("기본 배송지 조회 실패(무시): buyerId={}, reason={}", buyerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 채팅 주문에서 확정된 배송지를 auth-service에 기본 주소로 저장.
     * 실패해도 채팅 흐름에 영향 없음 (best-effort).
     */
    @Override
    public void saveDefaultAddress(Long buyerId, String zipCode, String roadAddress, String detailAddress) {
        try {
            restClient.post()
                    .uri("/auth/customers/addresses")
                    .header("X-User-Id", buyerId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "zipCode",       zipCode != null ? zipCode : "",
                            "roadAddress",   roadAddress,
                            "detailAddress", detailAddress != null ? detailAddress : "",
                            "isDefault",     true
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("배송지 auth-service 저장 완료: buyerId={}, address={}", buyerId, roadAddress);
        } catch (Exception e) {
            log.warn("배송지 auth-service 저장 실패(무시): buyerId={}, reason={}", buyerId, e.getMessage());
        }
    }

    /** auth-service 응답 내부 DTO — SavedAddressInfo를 대체 */
    private record SavedAddressResponse(
            String id, String zipCode, String roadAddress,
            String detailAddress, String label, boolean isDefault) {}
}
