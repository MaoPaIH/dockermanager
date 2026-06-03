package dev.sbox.panel.service;

import dev.sbox.panel.domain.NodeInstance;
import dev.sbox.panel.domain.NodeStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SubscriptionService {

    private static final Set<String> ALLOWED = Set.of(
            "auto", "v2rayn", "neko", "clash", "proxies", "shadowrocket", "sing-box", "sing-box-pc",
            "sing-box-phone", "sing-box2", "qr");

    private final NodeService nodeService;
    private final DockerService dockerService;

    public SubscriptionService(NodeService nodeService, DockerService dockerService) {
        this.nodeService = nodeService;
        this.dockerService = dockerService;
    }

    public SubscriptionContent get(String token, String type, String userAgent) {
        NodeInstance node = nodeService.findBySubscriptionToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        if (!node.isSubscriptionEnabled()) {
            throw new ResponseStatusException(HttpStatus.GONE, "订阅已关闭");
        }
        if (node.getStatus() == NodeStatus.EXPIRED || node.getStatus() == NodeStatus.TRAFFIC_LIMITED || node.getStatus() == NodeStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.GONE, "节点不可用");
        }
        if (node.getContainerId() == null || node.getContainerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "容器尚未就绪");
        }

        String normalizedType = normalizeType(type, userAgent);
        try {
            String body = dockerService.readSubscriptionFile(node.getContainerId(), normalizedType);
            if (body == null || body.isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅文件尚未生成");
            }
            return new SubscriptionContent(body, contentType(normalizedType));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "读取订阅被中断");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "读取订阅失败");
        }
    }

    private static String normalizeType(String type, String userAgent) {
        String requested = type == null || type.isBlank() ? "auto" : type.toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(requested)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅类型不支持");
        }
        if (!"auto".equals(requested)) {
            return requested;
        }
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("clash")) {
            return "clash";
        }
        if (ua.contains("shadowrocket")) {
            return "shadowrocket";
        }
        if (ua.contains("neko")) {
            return "neko";
        }
        if (ua.contains("sfm") || ua.contains("sfi") || ua.contains("sfa") || ua.contains("sing-box")) {
            return "sing-box";
        }
        return "v2rayn";
    }

    private static String contentType(String type) {
        return Map.of(
                "clash", "text/yaml; charset=utf-8",
                "proxies", "text/yaml; charset=utf-8",
                "sing-box", "application/json; charset=utf-8",
                "sing-box-pc", "application/json; charset=utf-8",
                "sing-box-phone", "application/json; charset=utf-8",
                "sing-box2", "application/json; charset=utf-8"
        ).getOrDefault(type, "text/plain; charset=utf-8");
    }

    public record SubscriptionContent(String body, String contentType) {
    }
}
