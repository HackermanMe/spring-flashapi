package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.annotation.EnableFlashApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableFlashApi(basePackages = "io.github.hackermanme.flashapi.entity")
public class SecurityTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityTestApplication.class, args);
    }
}
