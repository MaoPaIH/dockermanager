package dev.sbox.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sbox.panel.domain.NodeInstance;
import dev.sbox.panel.domain.NodeStatus;
import dev.sbox.panel.domain.PanelSettings;
import dev.sbox.panel.domain.ProtocolOption;
import dev.sbox.panel.form.CreateNodeForm;
import dev.sbox.panel.form.NodeEditableForm;
import dev.sbox.panel.repository.NodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NodeService {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final Pattern ENV_KEY = Pattern.compile("[A-Z_][A-Z0-9_]*");

    private final NodeRepository nodeRepository;
    private final SettingsService settingsService;
    private final PortAllocator portAllocator;
    private final DockerService dockerService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public NodeService(NodeRepository nodeRepository, SettingsService settingsService, PortAllocator portAllocator,
                       DockerService dockerService, AuditService auditService, ObjectMapper objectMapper) {
        this.nodeRepository = nodeRepository;
        this.settingsService = settingsService;
        this.portAllocator = portAllocator;
        this.dockerService = dockerService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<NodeInstance> listActive() {
        return nodeRepository.findAllActive();
    }

    public Optional<NodeInstance> find(long id) {
        return nodeRepository.findById(id);
    }

    public Optional<NodeInstance> findBySubscriptionToken(String token) {
        return nodeRepository.findBySubscriptionToken(token);
    }

    public CreateNodeForm defaultCreateForm() {
        PanelSettings settings = settingsService.get();
        var form = new CreateNodeForm();
        form.setName(settings.nodeNamePrefix() + "-" + Long.toString(System.currentTimeMillis(), 36));
        form.setProtocols(ProtocolOption.parseCsv(settings.defaultProtocols()).stream().sorted().toList());
        form.setTrafficLimitGb(settings.defaultTrafficLimitGb());
        form.setSubscriptionEnabled(settings.panelSubscriptionEnabled());
        if (settings.defaultValidityDays() > 0) {
            form.setExpiresAt(LocalDateTime.now().plusDays(settings.defaultValidityDays()));
        }
        return form;
    }

    public NodeEditableForm editableForm(NodeInstance node) {
        var form = new NodeEditableForm();
        form.setName(node.getName());
        form.setTrafficLimitGb(node.getTrafficLimitBytes() == null ? 0 : node.getTrafficLimitBytes() / GB);
        form.setExpiresAt(toLocal(node.getExpiresAt()));
        form.setSubscriptionEnabled(node.isSubscriptionEnabled());
        return form;
    }

    @Transactional
    public NodeInstance create(CreateNodeForm form, String actor) {
        PanelSettings settings = settingsService.get();
        int startPort = portAllocator.allocate(settings.portRangeStart(), settings.portRangeEnd(), settings.portBlockSize());
        var node = new NodeInstance();
        node.setName(form.getName().trim());
        node.setContainerName(uniqueContainerName(settings.containerPrefix(), form.getName()));
        node.setStatus(NodeStatus.CREATING);
        node.setImageName(settings.imageName());
        node.setPullLatest(settings.pullLatest());
        node.setStartPort(startPort);
        node.setPortBlockSize(settings.portBlockSize());
        node.setUuid(UUID.randomUUID().toString());
        node.setSubscriptionToken(randomToken());
        node.setSubscriptionEnabled(form.isSubscriptionEnabled());
        node.setProtocols(normalizeProtocols(form.getProtocols()));
        node.setServerIp(settings.serverIp());
        node.setCdn(settings.cdn());
        node.setNodeName(settings.nodeNamePrefix() + "-" + node.getName());
        node.setArgoDomain(settings.argoDomain());
        node.setArgoAuth(settings.argoAuth());
        node.setRealityPrivate(settings.realityPrivate());
        node.setExtraEnv(settings.extraEnv());
        node.setTrafficLimitBytes(toLimitBytes(form.getTrafficLimitGb()));
        node.setExpiresAt(toInstant(form.getExpiresAt()));
        Map<String, String> env = buildEnv(node);
        node.setEnvJson(toJson(env));
        long id = nodeRepository.insert(node);
        node.setId(id);

        auditService.info(actor, "NODE_CREATE_REQUESTED", id, node.getName(),
                "准备创建容器 " + node.getContainerName(), "startPort=" + startPort + ", protocols=" + node.getProtocols());
        try {
            String containerId = dockerService.createAndStart(node, toEnvList(env), settings.applyFscarmenCompatibilityPatch());
            node.setContainerId(containerId);
            node.setStatus(NodeStatus.RUNNING);
            nodeRepository.updateContainerCreated(id, containerId, NodeStatus.RUNNING);
            auditService.info(actor, "NODE_CREATED", id, node.getName(),
                    "容器已创建并启动", "containerId=" + containerId);
        } catch (Exception e) {
            node.setStatus(NodeStatus.ERROR);
            nodeRepository.updateStatus(id, NodeStatus.ERROR, e.getMessage());
            auditService.error(actor, "NODE_CREATE_FAILED", id, node.getName(),
                    "创建容器失败: " + e.getMessage(), stackMessage(e));
        }
        return node;
    }

    @Transactional
    public void updateEditable(long id, NodeEditableForm form, String actor) {
        NodeInstance node = requireNode(id);
        nodeRepository.updateEditable(id, form.getName().trim(), toLimitBytes(form.getTrafficLimitGb()),
                toInstant(form.getExpiresAt()), form.isSubscriptionEnabled());
        auditService.info(actor, "NODE_LIMITS_UPDATED", id, node.getName(),
                "节点数据库配置已更新", "trafficLimitGb=" + form.getTrafficLimitGb() + ", expiresAt=" + form.getExpiresAt());
    }

    @Transactional
    public void start(long id, String actor) {
        NodeInstance node = requireNode(id);
        requireContainer(node);
        try {
            dockerService.start(node.getContainerId());
            nodeRepository.markStarted(id, NodeStatus.RUNNING);
            auditService.info(actor, "NODE_STARTED", id, node.getName(), "容器已启动", null);
        } catch (Exception e) {
            nodeRepository.updateStatus(id, NodeStatus.ERROR, e.getMessage());
            auditService.error(actor, "NODE_START_FAILED", id, node.getName(), "启动容器失败: " + e.getMessage(), stackMessage(e));
        }
    }

    @Transactional
    public void stop(long id, String actor, String reason) {
        NodeInstance node = requireNode(id);
        requireContainer(node);
        try {
            if (dockerService.isRunning(node.getContainerId())) {
                dockerService.stop(node.getContainerId());
            }
            nodeRepository.updateStatus(id, NodeStatus.STOPPED, reason == null ? "手动停止" : reason);
            auditService.info(actor, "NODE_STOPPED", id, node.getName(), "容器已停止", reason);
        } catch (Exception e) {
            nodeRepository.updateStatus(id, NodeStatus.ERROR, e.getMessage());
            auditService.error(actor, "NODE_STOP_FAILED", id, node.getName(), "停止容器失败: " + e.getMessage(), stackMessage(e));
        }
    }

    @Transactional
    public void restart(long id, String actor) {
        NodeInstance node = requireNode(id);
        requireContainer(node);
        try {
            dockerService.restart(node.getContainerId());
            nodeRepository.markStarted(id, NodeStatus.RUNNING);
            auditService.info(actor, "NODE_RESTARTED", id, node.getName(), "容器已重启", null);
        } catch (Exception e) {
            nodeRepository.updateStatus(id, NodeStatus.ERROR, e.getMessage());
            auditService.error(actor, "NODE_RESTART_FAILED", id, node.getName(), "重启容器失败: " + e.getMessage(), stackMessage(e));
        }
    }

    @Transactional
    public void remove(long id, String actor) {
        NodeInstance node = requireNode(id);
        try {
            if (node.getContainerId() != null && !node.getContainerId().isBlank()) {
                dockerService.remove(node.getContainerId());
            }
            nodeRepository.updateStatus(id, NodeStatus.DELETED, "容器已删除");
            auditService.warn(actor, "NODE_DELETED", id, node.getName(), "容器已删除，数据库记录保留为已删除状态", null);
        } catch (Exception e) {
            nodeRepository.updateStatus(id, NodeStatus.ERROR, e.getMessage());
            auditService.error(actor, "NODE_DELETE_FAILED", id, node.getName(), "删除容器失败: " + e.getMessage(), stackMessage(e));
        }
    }

    @Transactional
    public void resetTraffic(long id, String actor) {
        NodeInstance node = requireNode(id);
        nodeRepository.resetTraffic(id);
        auditService.warn(actor, "NODE_TRAFFIC_RESET", id, node.getName(), "节点累计流量已清零", null);
    }

    @Transactional
    public void updateArgoUrlIfChanged(NodeInstance node, String listText) {
        String url = extractArgoUrl(listText, node.getUuid());
        if (url != null && !url.equals(node.getCurrentArgoUrl())) {
            nodeRepository.updateCurrentArgoUrl(node.getId(), url);
            auditService.info("system", "NODE_ARGO_CHANGED", node.getId(), node.getName(), "Argo 临时订阅域名已更新", url);
        }
    }

    public Map<String, String> buildEnv(NodeInstance node) {
        var env = new LinkedHashMap<String, String>();
        env.put("START_PORT", String.valueOf(node.getStartPort()));
        env.put("SERVER_IP", node.getServerIp());
        env.put("UUID", node.getUuid());
        env.put("CDN", node.getCdn() == null || node.getCdn().isBlank() ? "skk.moe" : node.getCdn());
        env.put("NODE_NAME", node.getNodeName());
        var enabled = ProtocolOption.parseCsv(node.getProtocols());
        for (ProtocolOption option : ProtocolOption.values()) {
            env.put(option.envName(), String.valueOf(enabled.contains(option.name())));
        }
        putIfPresent(env, "ARGO_DOMAIN", node.getArgoDomain());
        putIfPresent(env, "ARGO_AUTH", node.getArgoAuth());
        putIfPresent(env, "REALITY_PRIVATE", node.getRealityPrivate());
        env.putAll(parseExtraEnv(node.getExtraEnv()));
        return env;
    }

    public String subscriptionUrl(NodeInstance node, String publicBaseUrl) {
        return publicBaseUrl.replaceAll("/+$", "") + "/s/" + node.getSubscriptionToken() + "/auto";
    }

    public NodeInstance requireNode(long id) {
        return nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + id));
    }

    private static void requireContainer(NodeInstance node) {
        if (node.getContainerId() == null || node.getContainerId().isBlank()) {
            throw new IllegalStateException("节点尚未创建容器");
        }
    }

    private static List<String> toEnvList(Map<String, String> env) {
        return env.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private static Map<String, String> parseExtraEnv(String extraEnv) {
        var env = new LinkedHashMap<String, String>();
        if (extraEnv == null || extraEnv.isBlank()) {
            return env;
        }
        for (String rawLine : extraEnv.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                throw new IllegalArgumentException("高级环境变量格式错误: " + line);
            }
            String key = line.substring(0, split).trim().toUpperCase(Locale.ROOT);
            String value = line.substring(split + 1).trim();
            if (!ENV_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("高级环境变量名无效: " + key);
            }
            env.put(key, value);
        }
        return env;
    }

    private String toJson(Map<String, String> env) {
        try {
            return objectMapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化容器环境变量", e);
        }
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.put(key, value.trim());
        }
    }

    private static String normalizeProtocols(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            throw new IllegalArgumentException("至少需要启用一个协议");
        }
        var known = ProtocolOption.all().stream().map(Enum::name).collect(Collectors.toSet());
        var normalized = new ArrayList<String>();
        for (String protocol : protocols) {
            if (known.contains(protocol) && !normalized.contains(protocol)) {
                normalized.add(protocol);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("至少需要启用一个有效协议");
        }
        return String.join(",", normalized);
    }

    private static Long toLimitBytes(long gb) {
        return gb <= 0 ? null : gb * GB;
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static String uniqueContainerName(String prefix, String displayName) {
        String safeName = displayName == null ? "node" : displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safeName.isBlank()) {
            safeName = "node";
        }
        return prefix + safeName + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String extractArgoUrl(String listText, String uuid) {
        if (listText == null || listText.isBlank()) {
            return null;
        }
        var matcher = Pattern.compile("https://[A-Za-z0-9.-]+/" + Pattern.quote(uuid) + "/auto").matcher(listText);
        return matcher.find() ? matcher.group() : null;
    }

    private static String stackMessage(Exception e) {
        return e.getClass().getName() + ": " + e.getMessage();
    }
}
