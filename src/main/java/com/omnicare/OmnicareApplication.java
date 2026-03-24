package com.omnicare;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@EntityScan(basePackages = "com.omnicare")
@ConfigurationPropertiesScan
public class OmnicareApplication {
    public static void main(String[] args) {
        Dotenv.configure().ignoreIfMissing().systemProperties().load();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(OmnicareApplication.class, args);
    }
}
