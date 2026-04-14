package allmart.chatservice.adapter.client;

import allmart.chatservice.adapter.client.dto.SavedAddressInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * auth-service API 클라이언트.
 * GET  /auth/customers/addresses — 저장된 배송지 목록 조회 (isDefault 필터로 기본 주소 취득)
 * POST /auth/customers/addresses — 채팅 주문 시 확정된 배송지를 기본 주소로 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthServiceClient {

    @Qualifier("authServiceRestClient")
    private final RestClient restClient;

    /**
     * 소비자의 기본 배송지(isDefault=true) 반환.
     * 없으면 null.
     */
    public SavedAddressInfo getDefaultAddress(Long buyerId) {
        log.debug("기본 배송지 조회 요청: buyerId={}", buyerId);
        List<SavedAddressInfo> addresses = restClient.get()
                .uri("/auth/customers/addresses")
                .header("X-User-Id", buyerId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (addresses == null || addresses.isEmpty()) return null;

        return addresses.stream()
                .filter(SavedAddressInfo::isDefault)
                .findFirst()
                .orElse(null);
    }

    /**
     * 채팅 주문에서 확정된 배송지를 auth-service에 기본 주소로 저장.
     * 실패해도 채팅 흐름에 영향 없음 (best-effort).
     */
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
}