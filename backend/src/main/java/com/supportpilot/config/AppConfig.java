package com.supportpilot.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WebMvcConfigurer corsConfigurer(SupportPilotProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(properties.corsOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "DELETE")
                        .allowedHeaders("Content-Type", ApproverAuth.HEADER);
            }
        };
    }
}
