package vn.racehorse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RacehorseApplication {

    public static void main(String[] args) {
        SpringApplication.run(RacehorseApplication.class, args);
    }
}
