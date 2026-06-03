package dev.sbox.panel.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class SettingsForm {

    @NotBlank
    private String imageName;
    private boolean pullLatest;
    private boolean applyFscarmenCompatibilityPatch;

    @NotBlank
    private String containerPrefix;

    @NotBlank
    private String serverIp;

    private String cdn;
    private String nodeNamePrefix;
    private List<String> defaultProtocols = new ArrayList<>();

    @Min(100)
    @Max(65520)
    private int portRangeStart;

    @Min(100)
    @Max(65535)
    private int portRangeEnd;

    @Min(4)
    @Max(100)
    private int portBlockSize;

    @Min(0)
    private long defaultTrafficLimitGb;

    @Min(0)
    private int defaultValidityDays;

    private boolean panelSubscriptionEnabled;

    @Min(10)
    @Max(3600)
    private int statsIntervalSeconds;

    private String argoDomain;
    private String argoAuth;
    private String realityPrivate;
    private String extraEnv;

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

    public boolean isApplyFscarmenCompatibilityPatch() {
        return applyFscarmenCompatibilityPatch;
    }

    public void setApplyFscarmenCompatibilityPatch(boolean applyFscarmenCompatibilityPatch) {
        this.applyFscarmenCompatibilityPatch = applyFscarmenCompatibilityPatch;
    }

    public String getContainerPrefix() {
        return containerPrefix;
    }

    public void setContainerPrefix(String containerPrefix) {
        this.containerPrefix = containerPrefix;
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

    public String getNodeNamePrefix() {
        return nodeNamePrefix;
    }

    public void setNodeNamePrefix(String nodeNamePrefix) {
        this.nodeNamePrefix = nodeNamePrefix;
    }

    public List<String> getDefaultProtocols() {
        return defaultProtocols;
    }

    public void setDefaultProtocols(List<String> defaultProtocols) {
        this.defaultProtocols = defaultProtocols == null ? new ArrayList<>() : defaultProtocols;
    }

    public int getPortRangeStart() {
        return portRangeStart;
    }

    public void setPortRangeStart(int portRangeStart) {
        this.portRangeStart = portRangeStart;
    }

    public int getPortRangeEnd() {
        return portRangeEnd;
    }

    public void setPortRangeEnd(int portRangeEnd) {
        this.portRangeEnd = portRangeEnd;
    }

    public int getPortBlockSize() {
        return portBlockSize;
    }

    public void setPortBlockSize(int portBlockSize) {
        this.portBlockSize = portBlockSize;
    }

    public long getDefaultTrafficLimitGb() {
        return defaultTrafficLimitGb;
    }

    public void setDefaultTrafficLimitGb(long defaultTrafficLimitGb) {
        this.defaultTrafficLimitGb = defaultTrafficLimitGb;
    }

    public int getDefaultValidityDays() {
        return defaultValidityDays;
    }

    public void setDefaultValidityDays(int defaultValidityDays) {
        this.defaultValidityDays = defaultValidityDays;
    }

    public boolean isPanelSubscriptionEnabled() {
        return panelSubscriptionEnabled;
    }

    public void setPanelSubscriptionEnabled(boolean panelSubscriptionEnabled) {
        this.panelSubscriptionEnabled = panelSubscriptionEnabled;
    }

    public int getStatsIntervalSeconds() {
        return statsIntervalSeconds;
    }

    public void setStatsIntervalSeconds(int statsIntervalSeconds) {
        this.statsIntervalSeconds = statsIntervalSeconds;
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
}
