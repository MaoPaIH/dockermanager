package dev.sbox.panel;

import dev.sbox.panel.config.PanelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(PanelProperties.class)
public class PanelApplication {

    public static void main(String[] args) {
        SpringApplication.run(PanelApplication.class, args);
    }
}
