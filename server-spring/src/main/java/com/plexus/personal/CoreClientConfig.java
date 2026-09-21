package com.plexus.personal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CoreClientConfig {
    @Bean
    WebClient coreWebClient(
            WebClient.Builder builder,
            @Value("${plexus.core.base-url}") String coreBaseUrl
    ) {
        return builder.baseUrl(coreBaseUrl).build();
    }
}
