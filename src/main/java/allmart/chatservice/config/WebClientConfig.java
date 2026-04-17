package allmart.chatservice.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean("orderServiceRestClient")
    public RestClient orderServiceRestClient(
            @Value("${order-service.url}") String url) {
        return RestClient.builder()
                .baseUrl(url)
                .build();
    }

    @Bean("productServiceRestClient")
    public RestClient productServiceRestClient(
            @Value("${product-service.url}") String url) {
        return RestClient.builder()
                .baseUrl(url)
                .build();
    }

    @Bean("searchServiceRestClient")
    public RestClient searchServiceRestClient(
            @Value("${search-service.url}") String url) {
        return RestClient.builder()
                .baseUrl(url)
                .build();
    }

    @Bean("authServiceRestClient")
    public RestClient authServiceRestClient(
            @Value("${auth-service.url:http://localhost:8082}") String url) {
        return RestClient.builder()
                .baseUrl(url)
                .build();
    }
}
