package dev.sbox.panel.service;

import dev.sbox.panel.domain.AuditLevel;
import dev.sbox.panel.domain.AuditLog;
import dev.sbox.panel.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void info(String actor, String action, Long nodeId, String nodeName, String message, String details) {
        write(AuditLevel.INFO, actor, action, nodeId, nodeName, message, details);
    }

    public void warn(String actor, String action, Long nodeId, String nodeName, String message, String details) {
        write(AuditLevel.WARN, actor, action, nodeId, nodeName, message, details);
    }

    public void error(String actor, String action, Long nodeId, String nodeName, String message, String details) {
        write(AuditLevel.ERROR, actor, action, nodeId, nodeName, message, details);
    }

    public List<AuditLog> recent(int limit) {
        return repository.recent(limit);
    }

    public List<AuditLog> forNode(long nodeId, int limit) {
        return repository.forNode(nodeId, limit);
    }

    private void write(AuditLevel level, String actor, String action, Long nodeId, String nodeName, String message, String details) {
        repository.add(level, actor, action, nodeId == null ? null : "node", nodeId, nodeName, message, details);
        if (level == AuditLevel.ERROR) {
            log.error("[{}] {} - {}", action, nodeName == null ? "system" : nodeName, message);
        } else if (level == AuditLevel.WARN) {
            log.warn("[{}] {} - {}", action, nodeName == null ? "system" : nodeName, message);
        } else {
            log.info("[{}] {} - {}", action, nodeName == null ? "system" : nodeName, message);
        }
    }
}
