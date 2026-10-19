package com.example.connectmessenger;

import org.springframework.boot.SpringApplication;
import com.example.connectmessenger.config.CustomProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CustomProperties.class)
public class ConnectMessengerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectMessengerApplication.class, args);
    }

}
