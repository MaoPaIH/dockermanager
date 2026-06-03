package dev.sbox.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class FormatService {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public String bytes(long bytes) {
        if (bytes >= GB) {
            return String.format("%.2f GB", bytes / (double) GB);
        }
        long mb = 1024L * 1024L;
        if (bytes >= mb) {
            return String.format("%.1f MB", bytes / (double) mb);
        }
        long kb = 1024L;
        if (bytes >= kb) {
            return String.format("%.1f KB", bytes / (double) kb);
        }
        return bytes + " B";
    }

    public String instant(Instant instant) {
        return instant == null ? "-" : TIME.format(instant);
    }

    public String percent(long used, Long limit) {
        if (limit == null || limit <= 0) {
            return "-";
        }
        return String.format("%.1f%%", used * 100.0 / limit);
    }
}
