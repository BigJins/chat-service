package allmart.chatservice.application;

import allmart.chatservice.adapter.client.dto.OrderConfirmData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Claude 응답에서 ##ORDER_CONFIRM## 마커와 JSON을 파싱.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmParser {

    private static final String MARKER = "##ORDER_CONFIRM##";

    private final ObjectMapper objectMapper;

    public Optional<OrderConfirmData> parse(String fullResponse) {
        int markerIndex = fullResponse.indexOf(MARKER);
        if (markerIndex == -1) {
            return Optional.empty();
        }

        String jsonPart = fullResponse.substring(markerIndex + MARKER.length()).strip();
        // JSON이 여러 줄에 걸칠 수 있으므로 첫 번째 완전한 JSON 객체만 추출
        if (!jsonPart.startsWith("{")) {
            log.warn("ORDER_CONFIRM 마커 뒤에 JSON이 없음: {}", jsonPart);
            return Optional.empty();
        }

        try {
            OrderConfirmData data = objectMapper.readValue(jsonPart, OrderConfirmData.class);
            log.info("ORDER_CONFIRM 파싱 성공: payMethod={}, lines={}",
                    data.payMethod(), data.orderLines().size());
            return Optional.of(data);
        } catch (Exception e) {
            log.warn("ORDER_CONFIRM JSON 파싱 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
