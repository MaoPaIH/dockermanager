package dev.sbox.panel.controller;

import dev.sbox.panel.domain.ProtocolOption;
import dev.sbox.panel.form.SettingsForm;
import dev.sbox.panel.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @ModelAttribute("protocolOptions")
    public Object protocolOptions() {
        return ProtocolOption.all();
    }

    @GetMapping("/settings")
    public String edit(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", settingsService.toForm());
        }
        return "settings/edit";
    }

    @PostMapping("/settings")
    public String save(@Valid @ModelAttribute SettingsForm settingsForm, BindingResult bindingResult,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "settings/edit";
        }
        settingsService.save(settingsForm, actor(authentication));
        redirectAttributes.addFlashAttribute("notice", "系统默认配置已保存");
        return "redirect:/settings";
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}
