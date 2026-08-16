package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(TencentCosProperties.class)
@ConditionalOnProperty(prefix = "haoblog.media.cos", name = "enabled", havingValue = "true")
public class TencentCosConfiguration {
    @Bean
    HttpClient cosHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Bean
    ObjectStorage objectStorage(TencentCosProperties properties, HttpClient cosHttpClient) {
        return new TencentCosObjectStorage(properties, cosHttpClient);
    }
}
