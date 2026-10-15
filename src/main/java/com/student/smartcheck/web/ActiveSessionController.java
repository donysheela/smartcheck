package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActiveSessionController {
    private final SmartcheckService service;

    public ActiveSessionController(SmartcheckService service) {
        this.service = service;
    }

    @GetMapping("/api/active-sessions")
    public ActiveSessionCount activeSessions() {
        return new ActiveSessionCount(service.activeSessionCount());
    }

    private record ActiveSessionCount(long count) {
    }
}
