package com.omnicare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@EntityScan(basePackages = "com.omnicare")
@ConfigurationPropertiesScan
public class OmnicareApplication {
    public static void main(String[] args) {
        SpringApplication.run(OmnicareApplication.class, args);
    }
}
