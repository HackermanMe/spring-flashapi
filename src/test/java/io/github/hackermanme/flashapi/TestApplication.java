package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.annotation.EnableFlashApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFlashApi(basePackages = "io.github.hackermanme.flashapi.entity")
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
