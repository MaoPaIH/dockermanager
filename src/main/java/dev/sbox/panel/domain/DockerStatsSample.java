package dev.sbox.panel.domain;

public record DockerStatsSample(long rxBytes, long txBytes) {

    public long totalBytes() {
        return rxBytes + txBytes;
    }
}
