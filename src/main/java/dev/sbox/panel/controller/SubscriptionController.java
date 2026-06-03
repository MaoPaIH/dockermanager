package dev.sbox.panel.controller;

import dev.sbox.panel.service.SubscriptionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping({"/s/{token}", "/s/{token}/", "/s/{token}/{type}"})
    public ResponseEntity<String> get(@PathVariable String token,
                                      @PathVariable(required = false) String type,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        var content = subscriptionService.get(token, type, userAgent);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.body());
    }
}
