package allmart.chatservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Spring AI ChatClient — Spring Boot auto-configures ChatClient.Builder
     * via spring-ai-starter-model-anthropic (application.yml의 spring.ai.anthropic 설정 사용).
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
