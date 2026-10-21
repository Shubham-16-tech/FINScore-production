package com.finscore.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class FinScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinScoreApplication.class, args);
    }

    @Bean
    WebMvcConfigurer corsConfigurer(@Value("${finscore.allowed-origins}") String origins) {
        String[] allowed = origins.split(",");
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(allowed).allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*");
            }
        };
    }
}
