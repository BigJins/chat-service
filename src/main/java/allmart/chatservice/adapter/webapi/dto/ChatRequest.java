package allmart.chatservice.adapter.webapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotNull(message = "buyerId는 필수입니다")
        Long buyerId,

        @NotBlank(message = "message는 비어있을 수 없습니다")
        @Size(max = 500, message = "message는 500자 이하여야 합니다")
        String message
) {}
