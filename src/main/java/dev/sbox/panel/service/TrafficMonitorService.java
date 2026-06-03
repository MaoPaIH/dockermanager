package dev.sbox.panel.service;

import dev.sbox.panel.domain.NodeInstance;
import dev.sbox.panel.domain.NodeStatus;
import dev.sbox.panel.repository.NodeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrafficMonitorService {

    private final NodeRepository nodeRepository;
    private final SettingsService settingsService;
    private final DockerService dockerService;
    private final NodeService nodeService;
    private final AuditService auditService;
    private final AtomicLong lastPollMs = new AtomicLong(0);

    public TrafficMonitorService(NodeRepository nodeRepository, SettingsService settingsService, DockerService dockerService,
                                 NodeService nodeService, AuditService auditService) {
        this.nodeRepository = nodeRepository;
        this.settingsService = settingsService;
        this.dockerService = dockerService;
        this.nodeService = nodeService;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${panel.monitor-tick-ms:10000}")
    public void tick() {
        long nowMs = System.currentTimeMillis();
        int intervalSeconds = settingsService.get().statsIntervalSeconds();
        long previous = lastPollMs.get();
        if (previous > 0 && nowMs - previous < intervalSeconds * 1000L) {
            return;
        }
        if (!lastPollMs.compareAndSet(previous, nowMs)) {
            return;
        }
        pollNodes();
    }

    private void pollNodes() {
        for (NodeInstance node : nodeRepository.findAllForMonitor()) {
            try {
                pollNode(node);
            } catch (Exception e) {
                auditService.warn("system", "NODE_MONITOR_FAILED", node.getId(), node.getName(),
                        "节点监控失败: " + e.getMessage(), e.getClass().getName());
            }
        }
    }

    private void pollNode(NodeInstance node) throws InterruptedException, IOException {
        if (node.getContainerId() == null || node.getContainerId().isBlank()) {
            return;
        }

        Instant now = Instant.now();
        if (node.getExpiresAt() != null && now.isAfter(node.getExpiresAt())
                && node.getStatus() != NodeStatus.EXPIRED
                && node.getStatus() != NodeStatus.TRAFFIC_LIMITED
                && node.getStatus() != NodeStatus.DELETED) {
            stopForPolicy(node, NodeStatus.EXPIRED, "节点到期，已自动停止容器");
            return;
        }

        boolean running = dockerService.isRunning(node.getContainerId());
        if (!running) {
            if (node.getStatus() == NodeStatus.RUNNING || node.getStatus() == NodeStatus.CREATING) {
                String state = dockerService.stateText(node.getContainerId());
                nodeRepository.updateStatus(node.getId(), NodeStatus.STOPPED, "容器当前状态: " + state);
                auditService.warn("system", "NODE_STOPPED_EXTERNALLY", node.getId(), node.getName(),
                        "检测到容器不再运行", state);
            }
            return;
        }

        if (node.getStatus() != NodeStatus.RUNNING) {
            nodeRepository.markStarted(node.getId(), NodeStatus.RUNNING);
            auditService.info("system", "NODE_RUNNING_DETECTED", node.getId(), node.getName(), "检测到容器正在运行", null);
        }

        updateArgoUrl(node);
        updateTraffic(node, now);
    }

    private void updateArgoUrl(NodeInstance node) throws InterruptedException, IOException {
        String list = dockerService.readListFile(node.getContainerId());
        nodeService.updateArgoUrlIfChanged(node, list);
    }

    private void updateTraffic(NodeInstance node, Instant sampledAt) throws InterruptedException, IOException {
        var sample = dockerService.stats(node.getContainerId());
        long currentRx = sample.rxBytes();
        long currentTx = sample.txBytes();

        if (node.getLastStatsAt() == null && node.getLastRxBytes() == 0 && node.getLastTxBytes() == 0) {
            nodeRepository.updateTraffic(node.getId(), node.getTrafficUsedBytes(), currentRx, currentTx, sampledAt);
            auditService.info("system", "NODE_TRAFFIC_BASELINE", node.getId(), node.getName(),
                    "已初始化 Docker stats 流量基线", "rx=" + currentRx + ", tx=" + currentTx);
            return;
        }

        long rxDelta = currentRx >= node.getLastRxBytes() ? currentRx - node.getLastRxBytes() : currentRx;
        long txDelta = currentTx >= node.getLastTxBytes() ? currentTx - node.getLastTxBytes() : currentTx;
        long used = node.getTrafficUsedBytes() + Math.max(0, rxDelta) + Math.max(0, txDelta);
        nodeRepository.updateTraffic(node.getId(), used, currentRx, currentTx, sampledAt);

        Long limit = node.getTrafficLimitBytes();
        if (limit != null && limit > 0 && used >= limit) {
            stopForPolicy(node, NodeStatus.TRAFFIC_LIMITED, "流量达到上限，已自动停止容器");
        }
    }

    private void stopForPolicy(NodeInstance node, NodeStatus status, String reason) {
        try {
            if (dockerService.isRunning(node.getContainerId())) {
                dockerService.stop(node.getContainerId());
            }
        } catch (Exception e) {
            auditService.error("system", "NODE_POLICY_STOP_FAILED", node.getId(), node.getName(),
                    "策略停止容器失败: " + e.getMessage(), e.getClass().getName());
        }
        nodeRepository.updateStatus(node.getId(), status, reason);
        auditService.warn("system", status == NodeStatus.EXPIRED ? "NODE_EXPIRED" : "NODE_TRAFFIC_LIMITED",
                node.getId(), node.getName(), reason, null);
    }
}
