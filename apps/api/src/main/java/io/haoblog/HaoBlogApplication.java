package io.haoblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HaoBlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(HaoBlogApplication.class, args);
    }
}
