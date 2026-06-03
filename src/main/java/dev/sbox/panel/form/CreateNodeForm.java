package dev.sbox.panel.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CreateNodeForm {

    @NotBlank
    private String name;

    private List<String> protocols = new ArrayList<>();

    @Min(0)
    private long trafficLimitGb;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;
    private boolean subscriptionEnabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(List<String> protocols) {
        this.protocols = protocols == null ? new ArrayList<>() : protocols;
    }

    public long getTrafficLimitGb() {
        return trafficLimitGb;
    }

    public void setTrafficLimitGb(long trafficLimitGb) {
        this.trafficLimitGb = trafficLimitGb;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled;
    }

    public void setSubscriptionEnabled(boolean subscriptionEnabled) {
        this.subscriptionEnabled = subscriptionEnabled;
    }
}
