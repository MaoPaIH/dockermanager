package dev.sbox.panel.config;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class DockerConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    @Bean
    DockerClient dockerClient(PanelProperties properties) {
        var builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        String configuredHost = properties.getDockerHost();
        if (configuredHost != null && !configuredHost.isBlank()) {
            builder.withDockerHost(configuredHost.trim());
        } else if (Files.exists(Path.of("/var/run/docker.sock"))) {
            builder.withDockerHost("unix:///var/run/docker.sock");
        }
        var config = builder.build();
        log.info("Docker host: {}", config.getDockerHost());
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(64)
                .connectionTimeout(Duration.ofSeconds(20))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
