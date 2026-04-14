package allmart.chatservice.domain.session;

/**
 * 수집된 배송지 — ChatSession에 명시적으로 저장.
 * LLM 대화 이력이 아닌 구조체로 관리해 슬라이딩 윈도우 trimming 시에도 유실되지 않음.
 */
public record DeliveryAddress(
        String zipCode,
        String roadAddress,
        String detailAddress,
        String deliveryRequest   // 배달 요청사항 (없으면 null)
) {}
