package com.seeex.tech.web;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(scanBasePackages = "com.seeex.tech")
public class WebApplication {
    public static void main(String[] args) { SpringApplication.run(WebApplication.class, args); }
    @Bean
    public ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
}
