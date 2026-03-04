package com.ExcelImport.PdfToExcel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/api/pdf")
                        .allowedOrigins("https://pdfextractionfront.netlify.app")
                        .allowedHeaders("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");

                registry.addMapping("/api/auth")
                        .allowedOrigins("https://pdfextractionfront.netlify.app")
                        .allowedHeaders("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}
