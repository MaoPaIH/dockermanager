package dev.sbox.panel.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "panel")
public class PanelProperties {

    @NotBlank
    private String adminUsername = "admin";

    @NotBlank
    private String adminPassword = "change-me-now";

    @NotBlank
    private String publicBaseUrl = "http://127.0.0.1:8080";

    private long monitorTickMs = 10_000;

    private String dockerHost = "";

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMonitorTickMs() {
        return monitorTickMs;
    }

    public void setMonitorTickMs(long monitorTickMs) {
        this.monitorTickMs = monitorTickMs;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
    }
}
