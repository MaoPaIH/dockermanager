package dev.sbox.panel.domain;

public record PanelSettings(
        String imageName,
        boolean pullLatest,
        boolean applyFscarmenCompatibilityPatch,
        String containerPrefix,
        String serverIp,
        String cdn,
        String nodeNamePrefix,
        String defaultProtocols,
        int portRangeStart,
        int portRangeEnd,
        int portBlockSize,
        long defaultTrafficLimitGb,
        int defaultValidityDays,
        boolean panelSubscriptionEnabled,
        int statsIntervalSeconds,
        String argoDomain,
        String argoAuth,
        String realityPrivate,
        String extraEnv
) {
}
