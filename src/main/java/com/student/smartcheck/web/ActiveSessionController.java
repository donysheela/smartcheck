package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
import com.student.smartcheck.domain.TrackingSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;

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
    public SessionCompletions completedSessions(
            @RequestParam(required = false) Long afterMicros,
            @RequestParam(required = false) Long afterId) {
        Instant checkedAt = Instant.now();
        Instant dayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long cursorMicros = afterMicros == null ? toEpochMicros(dayStart) : afterMicros;
        long cursorId = afterId == null ? 0 : afterId;
        Instant after = fromEpochMicros(cursorMicros);
        List<CompletedSession> completedSessions = service.completedSessionsSince(checkedAt, after, cursorId)
                .stream()
                .map(session -> {
                    Instant completionTime = session.isActive() ? session.getEndsAt() : session.getCompletedAt();
                    return new CompletedSession(session.getId(), session.getStudent().getName(),
                            completionTime.toEpochMilli(), toEpochMicros(completionTime));
                })
                .toList();
        CompletedSession latest = completedSessions.stream()
                .max(Comparator.comparingLong(CompletedSession::cursorAtMicros)
                        .thenComparingLong(CompletedSession::id))
                .orElse(null);
        long nextAfterMicros = latest == null ? cursorMicros : latest.cursorAtMicros();
        long nextAfterId = latest == null ? cursorId : latest.id();
        return new SessionCompletions(completedSessions, service.completedSessionCountToday(),
                nextAfterMicros, nextAfterId);
    }

    private long toEpochMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000;
    }

    private Instant fromEpochMicros(long micros) {
        return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                Math.floorMod(micros, 1_000_000L) * 1_000L);
    }

    private record ActiveSessionCount(long count) {
    }

    private record CompletedSession(Long id, String studentName, long completedAt, long cursorAtMicros) {
    }

    private record SessionCompletions(List<CompletedSession> sessions, long todayCount,
                                      long nextAfterMicros, long nextAfterId) {
    }
}
