package dev.sbox.panel.service;

import dev.sbox.panel.domain.PanelSettings;
import dev.sbox.panel.domain.ProtocolOption;
import dev.sbox.panel.form.SettingsForm;
import dev.sbox.panel.repository.DatabaseInitializer;
import dev.sbox.panel.repository.SettingRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SettingsService {

    private final SettingRepository repository;
    private final AuditService auditService;
    private final DatabaseInitializer databaseInitializer;

    public SettingsService(SettingRepository repository, AuditService auditService, DatabaseInitializer databaseInitializer) {
        this.repository = repository;
        this.auditService = auditService;
        this.databaseInitializer = databaseInitializer;
    }

    @PostConstruct
    void ensureDefaults() {
        databaseInitializer.initialize();
        var current = repository.findAll();
        defaults().forEach((key, value) -> {
            if (!current.containsKey(key)) {
                repository.put(key, value);
            }
        });
    }

    public PanelSettings get() {
        var values = new LinkedHashMap<>(defaults());
        values.putAll(repository.findAll());
        return new PanelSettings(
                values.get("imageName"),
                bool(values.get("pullLatest")),
                bool(values.get("applyFscarmenCompatibilityPatch")),
                values.get("containerPrefix"),
                values.get("serverIp"),
                values.get("cdn"),
                values.get("nodeNamePrefix"),
                values.get("defaultProtocols"),
                integer(values.get("portRangeStart")),
                integer(values.get("portRangeEnd")),
                integer(values.get("portBlockSize")),
                longValue(values.get("defaultTrafficLimitGb")),
                integer(values.get("defaultValidityDays")),
                bool(values.get("panelSubscriptionEnabled")),
                integer(values.get("statsIntervalSeconds")),
                values.get("argoDomain"),
                values.get("argoAuth"),
                values.get("realityPrivate"),
                values.get("extraEnv"));
    }

    public SettingsForm toForm() {
        var settings = get();
        var form = new SettingsForm();
        form.setImageName(settings.imageName());
        form.setPullLatest(settings.pullLatest());
        form.setApplyFscarmenCompatibilityPatch(settings.applyFscarmenCompatibilityPatch());
        form.setContainerPrefix(settings.containerPrefix());
        form.setServerIp(settings.serverIp());
        form.setCdn(settings.cdn());
        form.setNodeNamePrefix(settings.nodeNamePrefix());
        form.setDefaultProtocols(ProtocolOption.parseCsv(settings.defaultProtocols()).stream().sorted().toList());
        form.setPortRangeStart(settings.portRangeStart());
        form.setPortRangeEnd(settings.portRangeEnd());
        form.setPortBlockSize(settings.portBlockSize());
        form.setDefaultTrafficLimitGb(settings.defaultTrafficLimitGb());
        form.setDefaultValidityDays(settings.defaultValidityDays());
        form.setPanelSubscriptionEnabled(settings.panelSubscriptionEnabled());
        form.setStatsIntervalSeconds(settings.statsIntervalSeconds());
        form.setArgoDomain(settings.argoDomain());
        form.setArgoAuth(settings.argoAuth());
        form.setRealityPrivate(settings.realityPrivate());
        form.setExtraEnv(settings.extraEnv());
        return form;
    }

    public void save(SettingsForm form, String actor) {
        var values = new LinkedHashMap<String, String>();
        values.put("imageName", text(form.getImageName()));
        values.put("pullLatest", String.valueOf(form.isPullLatest()));
        values.put("applyFscarmenCompatibilityPatch", String.valueOf(form.isApplyFscarmenCompatibilityPatch()));
        values.put("containerPrefix", text(form.getContainerPrefix()));
        values.put("serverIp", text(form.getServerIp()));
        values.put("cdn", text(form.getCdn()));
        values.put("nodeNamePrefix", text(form.getNodeNamePrefix()));
        values.put("defaultProtocols", form.getDefaultProtocols().stream().collect(Collectors.joining(",")));
        values.put("portRangeStart", String.valueOf(form.getPortRangeStart()));
        values.put("portRangeEnd", String.valueOf(form.getPortRangeEnd()));
        values.put("portBlockSize", String.valueOf(form.getPortBlockSize()));
        values.put("defaultTrafficLimitGb", String.valueOf(form.getDefaultTrafficLimitGb()));
        values.put("defaultValidityDays", String.valueOf(form.getDefaultValidityDays()));
        values.put("panelSubscriptionEnabled", String.valueOf(form.isPanelSubscriptionEnabled()));
        values.put("statsIntervalSeconds", String.valueOf(form.getStatsIntervalSeconds()));
        values.put("argoDomain", text(form.getArgoDomain()));
        values.put("argoAuth", text(form.getArgoAuth()));
        values.put("realityPrivate", text(form.getRealityPrivate()));
        values.put("extraEnv", text(form.getExtraEnv()));
        repository.putAll(values);
        auditService.info(actor, "SETTINGS_UPDATED", null, null, "系统默认配置已更新", null);
    }

    private Map<String, String> defaults() {
        var defaults = new LinkedHashMap<String, String>();
        defaults.put("imageName", "fscarmen/sb:latest");
        defaults.put("pullLatest", "false");
        defaults.put("applyFscarmenCompatibilityPatch", "true");
        defaults.put("containerPrefix", "sb-node-");
        defaults.put("serverIp", "127.0.0.1");
        defaults.put("cdn", "skk.moe");
        defaults.put("nodeNamePrefix", "friend");
        defaults.put("defaultProtocols", ProtocolOption.defaultCsv());
        defaults.put("portRangeStart", "10000");
        defaults.put("portRangeEnd", "60000");
        defaults.put("portBlockSize", "20");
        defaults.put("defaultTrafficLimitGb", "100");
        defaults.put("defaultValidityDays", "30");
        defaults.put("panelSubscriptionEnabled", "true");
        defaults.put("statsIntervalSeconds", "30");
        defaults.put("argoDomain", "");
        defaults.put("argoAuth", "");
        defaults.put("realityPrivate", "");
        defaults.put("extraEnv", "");
        return defaults;
    }

    private static boolean bool(String value) {
        return Boolean.parseBoolean(value);
    }

    private static int integer(String value) {
        return Integer.parseInt(value);
    }

    private static long longValue(String value) {
        return Long.parseLong(value);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
