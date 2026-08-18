package com.flashmind.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicClientConfig {

    /**
     * Claude API client, built once at startup and reused for every request.
     * The SDK default timeout is 10 minutes — far too long to hold a Spring MVC
     * request thread — so it is always overridden from configuration.
     */
    @Bean
    public AnthropicClient anthropicClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.timeout-seconds}") long timeoutSeconds) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
