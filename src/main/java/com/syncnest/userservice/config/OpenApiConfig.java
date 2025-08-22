package com.syncnest.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("User Management API")
                        .version("1.0.0")
                        .description("API documentation for the User Management Service for ETA")
                        .termsOfService("http://your-terms-of-service-url.com")
                        .contact(new Contact().name("Support Team")
                                .url("http://your-support-url.com")
                                .email("support@yourdomain.com"))
                        .license(new License().name("Apache 2.0")
                                .url("http://springdoc.org")));
    }
}