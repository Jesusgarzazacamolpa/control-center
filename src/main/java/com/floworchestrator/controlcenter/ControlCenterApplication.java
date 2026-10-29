package com.floworchestrator.controlcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Punto de entrada del servicio Control Center (Spring Boot + PostgreSQL).
 * Arranca la aplicación y habilita CORS para el frontend local.
 */
@SpringBootApplication
public class ControlCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlCenterApplication.class, args);
    }

    /**
     * Permite peticiones del frontend en {@code http://localhost:4200} con credenciales.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
