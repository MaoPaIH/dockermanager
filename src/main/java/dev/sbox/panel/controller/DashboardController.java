package dev.sbox.panel.controller;

import dev.sbox.panel.config.PanelProperties;
import dev.sbox.panel.service.AuditService;
import dev.sbox.panel.service.FormatService;
import dev.sbox.panel.service.NodeService;
import dev.sbox.panel.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final NodeService nodeService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final FormatService formatService;
    private final PanelProperties panelProperties;

    public DashboardController(NodeService nodeService, SettingsService settingsService, AuditService auditService,
                               FormatService formatService, PanelProperties panelProperties) {
        this.nodeService = nodeService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.formatService = formatService;
        this.panelProperties = panelProperties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("nodes", nodeService.listActive());
        model.addAttribute("settings", settingsService.get());
        model.addAttribute("logs", auditService.recent(12));
        model.addAttribute("fmt", formatService);
        model.addAttribute("publicBaseUrl", panelProperties.getPublicBaseUrl());
        return "index";
    }
}
