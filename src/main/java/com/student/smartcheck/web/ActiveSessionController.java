package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
import com.student.smartcheck.domain.TrackingSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

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

    @GetMapping("/api/session-completions")
    public SessionCompletions completedSessions() {
        Instant checkedAt = Instant.now();
        List<CompletedSession> completedSessions = service.completedSessionsToday(checkedAt)
                .stream()
                .map(session -> new CompletedSession(session.getId(), session.getStudent().getName(),
                        (session.getCompletedAt() == null ? session.getEndsAt() : session.getCompletedAt())
                                .toEpochMilli()))
                .toList();
        return new SessionCompletions(completedSessions, service.completedSessionCountToday());
    }

    private record ActiveSessionCount(long count) {
    }

    private record CompletedSession(Long id, String studentName, long completedAt) {
    }

    private record SessionCompletions(List<CompletedSession> sessions, long todayCount) {
    }
}
