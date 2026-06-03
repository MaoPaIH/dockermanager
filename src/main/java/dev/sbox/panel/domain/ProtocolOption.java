package dev.sbox.panel.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum ProtocolOption {
    XTLS_REALITY("XTLS_REALITY", "XTLS + Reality", true),
    HYSTERIA2("HYSTERIA2", "Hysteria2", true),
    TUIC("TUIC", "TUIC", true),
    SHADOWTLS("SHADOWTLS", "ShadowTLS", true),
    SHADOWSOCKS("SHADOWSOCKS", "Shadowsocks", true),
    TROJAN("TROJAN", "Trojan", true),
    VMESS_WS("VMESS_WS", "VMess WS", true),
    VLESS_WS("VLESS_WS", "VLESS WS TLS", true),
    H2_REALITY("H2_REALITY", "H2 + Reality", true),
    GRPC_REALITY("GRPC_REALITY", "gRPC + Reality", true),
    ANYTLS("ANYTLS", "AnyTLS", true);

    private final String envName;
    private final String label;
    private final boolean defaultEnabled;

    ProtocolOption(String envName, String label, boolean defaultEnabled) {
        this.envName = envName;
        this.label = label;
        this.defaultEnabled = defaultEnabled;
    }

    public String envName() {
        return envName;
    }

    public String label() {
        return label;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public static List<ProtocolOption> all() {
        return Arrays.asList(values());
    }

    public static String defaultCsv() {
        return all().stream()
                .filter(ProtocolOption::defaultEnabled)
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }
}
