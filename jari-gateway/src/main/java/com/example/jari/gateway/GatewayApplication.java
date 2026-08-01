package com.example.jari.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
// jari-security is scanned for JwtUtils, which AuthenticationFilter needs.
// jari-common is deliberately absent: its only scannable bean is an MVC
// @RestControllerAdvice that cannot resolve its WebRequest argument in WebFlux.
@ComponentScan(basePackages = {"com.example.jari.gateway", "com.example.jari.security"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
