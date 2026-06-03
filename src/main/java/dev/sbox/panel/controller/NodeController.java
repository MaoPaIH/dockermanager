package dev.sbox.panel.controller;

import dev.sbox.panel.config.PanelProperties;
import dev.sbox.panel.domain.ProtocolOption;
import dev.sbox.panel.form.CreateNodeForm;
import dev.sbox.panel.form.NodeEditableForm;
import dev.sbox.panel.service.AuditService;
import dev.sbox.panel.service.DockerService;
import dev.sbox.panel.service.FormatService;
import dev.sbox.panel.service.NodeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class NodeController {

    private final NodeService nodeService;
    private final DockerService dockerService;
    private final AuditService auditService;
    private final FormatService formatService;
    private final PanelProperties panelProperties;

    public NodeController(NodeService nodeService, DockerService dockerService, AuditService auditService,
                          FormatService formatService, PanelProperties panelProperties) {
        this.nodeService = nodeService;
        this.dockerService = dockerService;
        this.auditService = auditService;
        this.formatService = formatService;
        this.panelProperties = panelProperties;
    }

    @ModelAttribute("protocolOptions")
    public Object protocolOptions() {
        return ProtocolOption.all();
    }

    @GetMapping("/nodes/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("createNodeForm")) {
            model.addAttribute("createNodeForm", nodeService.defaultCreateForm());
        }
        return "nodes/new";
    }

    @PostMapping("/nodes")
    public String create(@Valid @ModelAttribute CreateNodeForm createNodeForm, BindingResult bindingResult,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "nodes/new";
        }
        var node = nodeService.create(createNodeForm, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "节点创建任务已执行: " + node.getName());
        return "redirect:/nodes/" + node.getId();
    }

    @GetMapping("/nodes/{id}")
    public String detail(@PathVariable long id, Model model) {
        var node = nodeService.requireNode(id);
        model.addAttribute("node", node);
        model.addAttribute("editableForm", nodeService.editableForm(node));
        model.addAttribute("subscriptionUrl", nodeService.subscriptionUrl(node, panelProperties.getPublicBaseUrl()));
        model.addAttribute("logs", auditService.forNode(id, 30));
        model.addAttribute("fmt", formatService);
        model.addAttribute("dockerLogs", dockerLogs(node.getContainerId()));
        model.addAttribute("boxLogs", boxLogs(node.getContainerId()));
        model.addAttribute("listText", listText(node.getContainerId()));
        return "nodes/detail";
    }

    @PostMapping("/nodes/{id}/editable")
    public String updateEditable(@PathVariable long id, @Valid @ModelAttribute NodeEditableForm editableForm,
                                 BindingResult bindingResult, Authentication authentication,
                                 RedirectAttributes redirectAttributes, Model model) {
        if (bindingResult.hasErrors()) {
            var node = nodeService.requireNode(id);
            model.addAttribute("node", node);
            model.addAttribute("subscriptionUrl", nodeService.subscriptionUrl(node, panelProperties.getPublicBaseUrl()));
            model.addAttribute("logs", auditService.forNode(id, 30));
            model.addAttribute("fmt", formatService);
            model.addAttribute("dockerLogs", dockerLogs(node.getContainerId()));
            model.addAttribute("boxLogs", boxLogs(node.getContainerId()));
            model.addAttribute("listText", listText(node.getContainerId()));
            return "nodes/detail";
        }
        nodeService.updateEditable(id, editableForm, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "节点配置已保存");
        return "redirect:/nodes/" + id;
    }

    @PostMapping("/nodes/{id}/start")
    public String start(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        nodeService.start(id, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "启动命令已执行");
        return "redirect:/nodes/" + id;
    }

    @PostMapping("/nodes/{id}/stop")
    public String stop(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        nodeService.stop(id, actor(authentication), "手动停止");
        redirectAttributes.addFlashAttribute("notice", "停止命令已执行");
        return "redirect:/nodes/" + id;
    }

    @PostMapping("/nodes/{id}/restart")
    public String restart(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        nodeService.restart(id, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "重启命令已执行");
        return "redirect:/nodes/" + id;
    }

    @PostMapping("/nodes/{id}/reset-traffic")
    public String resetTraffic(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        nodeService.resetTraffic(id, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "流量计数已清零");
        return "redirect:/nodes/" + id;
    }

    @PostMapping("/nodes/{id}/delete")
    public String delete(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        nodeService.remove(id, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "删除命令已执行");
        return "redirect:/";
    }

    private String dockerLogs(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return "容器尚未创建";
        }
        try {
            return dockerService.containerLogs(containerId, 160);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "读取 Docker logs 被中断";
        } catch (IOException e) {
            return "读取 Docker logs 失败: " + e.getMessage();
        }
    }

    private String boxLogs(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return "容器尚未创建";
        }
        try {
            return dockerService.readBoxLog(containerId, 160);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "读取 box.log 被中断";
        } catch (IOException e) {
            return "读取 box.log 失败: " + e.getMessage();
        }
    }

    private String listText(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return "容器尚未创建";
        }
        try {
            return dockerService.readListFile(containerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "读取 list 被中断";
        } catch (IOException e) {
            return "读取 list 失败: " + e.getMessage();
        }
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}
