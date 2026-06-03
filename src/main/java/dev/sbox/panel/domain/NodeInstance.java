package dev.sbox.panel.domain;

import java.time.Instant;

public class NodeInstance {

    private Long id;
    private String name;
    private String containerName;
    private String containerId;
    private NodeStatus status;
    private String imageName;
    private boolean pullLatest;
    private int startPort;
    private int portBlockSize;
    private String uuid;
    private String subscriptionToken;
    private boolean subscriptionEnabled;
    private String protocols;
    private String serverIp;
    private String cdn;
    private String nodeName;
    private String argoDomain;
    private String argoAuth;
    private String realityPrivate;
    private String extraEnv;
    private String envJson;
    private Long trafficLimitBytes;
    private long trafficUsedBytes;
    private long lastRxBytes;
    private long lastTxBytes;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastStartedAt;
    private Instant stoppedAt;
    private Instant lastStatsAt;
    private String stopReason;
    private String currentArgoUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public boolean isPullLatest() {
        return pullLatest;
    }

    public void setPullLatest(boolean pullLatest) {
        this.pullLatest = pullLatest;
    }

    public int getStartPort() {
        return startPort;
    }

    public void setStartPort(int startPort) {
        this.startPort = startPort;
    }

    public int getPortBlockSize() {
        return portBlockSize;
    }

    public void setPortBlockSize(int portBlockSize) {
        this.portBlockSize = portBlockSize;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getSubscriptionToken() {
        return subscriptionToken;
    }

    public void setSubscriptionToken(String subscriptionToken) {
        this.subscriptionToken = subscriptionToken;
    }

    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled;
    }

    public void setSubscriptionEnabled(boolean subscriptionEnabled) {
        this.subscriptionEnabled = subscriptionEnabled;
    }

    public String getProtocols() {
        return protocols;
    }

    public void setProtocols(String protocols) {
        this.protocols = protocols;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getCdn() {
        return cdn;
    }

    public void setCdn(String cdn) {
        this.cdn = cdn;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getArgoDomain() {
        return argoDomain;
    }

    public void setArgoDomain(String argoDomain) {
        this.argoDomain = argoDomain;
    }

    public String getArgoAuth() {
        return argoAuth;
    }

    public void setArgoAuth(String argoAuth) {
        this.argoAuth = argoAuth;
    }

    public String getRealityPrivate() {
        return realityPrivate;
    }

    public void setRealityPrivate(String realityPrivate) {
        this.realityPrivate = realityPrivate;
    }

    public String getExtraEnv() {
        return extraEnv;
    }

    public void setExtraEnv(String extraEnv) {
        this.extraEnv = extraEnv;
    }

    public String getEnvJson() {
        return envJson;
    }

    public void setEnvJson(String envJson) {
        this.envJson = envJson;
    }

    public Long getTrafficLimitBytes() {
        return trafficLimitBytes;
    }

    public void setTrafficLimitBytes(Long trafficLimitBytes) {
        this.trafficLimitBytes = trafficLimitBytes;
    }

    public long getTrafficUsedBytes() {
        return trafficUsedBytes;
    }

    public void setTrafficUsedBytes(long trafficUsedBytes) {
        this.trafficUsedBytes = trafficUsedBytes;
    }

    public long getLastRxBytes() {
        return lastRxBytes;
    }

    public void setLastRxBytes(long lastRxBytes) {
        this.lastRxBytes = lastRxBytes;
    }

    public long getLastTxBytes() {
        return lastTxBytes;
    }

    public void setLastTxBytes(long lastTxBytes) {
        this.lastTxBytes = lastTxBytes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(Instant lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }

    public Instant getLastStatsAt() {
        return lastStatsAt;
    }

    public void setLastStatsAt(Instant lastStatsAt) {
        this.lastStatsAt = lastStatsAt;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getCurrentArgoUrl() {
        return currentArgoUrl;
    }

    public void setCurrentArgoUrl(String currentArgoUrl) {
        this.currentArgoUrl = currentArgoUrl;
    }
}
