package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.annotation.EnableFlashApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableCaching
@EnableFlashApi(basePackages = "io.github.hackermanme.flashapi.entity")
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
