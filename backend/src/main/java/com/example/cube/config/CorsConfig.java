package com.example.cube.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS is only needed when the frontend is served from another origin (local dev server).
 * In production the frontend is deployed on the same WebLogic origin and the list stays empty.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${cube.cors.allowed-origins:}") String origins) {
        List<String> allowed = new ArrayList<>();
        for (String origin : origins.split(",")) {
            if (!origin.trim().isEmpty()) {
                allowed.add(origin.trim());
            }
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowed.isEmpty()) {
            return source;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(Arrays.asList("Content-Disposition", "Content-Length", "Content-Type"));
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
