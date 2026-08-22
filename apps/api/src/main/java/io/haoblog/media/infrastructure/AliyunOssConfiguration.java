package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AliyunOssProperties.class)
@ConditionalOnProperty(prefix = "haoblog.media.oss", name = "enabled", havingValue = "true")
public class AliyunOssConfiguration {
    @Bean
    HttpClient ossHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Bean
    ObjectStorage objectStorage(AliyunOssProperties properties, HttpClient ossHttpClient) {
        return new AliyunOssObjectStorage(properties, ossHttpClient);
    }
}
