package com.student.smartcheck.application;

import com.student.smartcheck.domain.TrackingSession;
import com.student.smartcheck.domain.TrackingSessionRepository;
import com.student.smartcheck.util.DateTimeDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private final TrackingSessionRepository sessions;
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public ReminderService(TrackingSessionRepository sessions, JavaMailSender mailSender,
                           @Value("${app.reminders.enabled:false}") boolean enabled,
                           @Value("${app.mail.from:}") String from) {
        this.sessions = sessions;
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${app.reminders.check-interval:30000}")
    @Transactional
    public void sendDueReminders() {
        if (!enabled) {
            Instant now = Instant.now();
            sessions.findByActiveTrue().stream()
                    .filter(session -> !now.isBefore(session.getEndsAt()))
                    .forEach(TrackingSession::complete);
            return;
        }
        Instant now = Instant.now();
        for (TrackingSession session : sessions.findByActiveTrue()) {
            try {
                if (session.isAlertsEnabled() && !session.isTenMinuteReminderSent()
                        && !now.isBefore(session.getEndsAt().minus(Duration.ofMinutes(10)))) {
                    send(session, "10-minute session warning",
                            session.getStudent().getName() + "'s session will end at "
                                    + DateTimeDisplay.format(session.getEndsAt()) + ".");
                    session.markTenMinuteReminderSent();
                }
                if (session.isAlertsEnabled() && !session.isFinalAlertSent()
                        && !now.isBefore(session.getEndsAt())) {
                    send(session, "Session time reached",
                            session.getStudent().getName() + "'s configured session time has ended.");
                    session.markFinalAlertSent();
                }
            } catch (MailException exception) {
                log.error("Could not send a session alert for session {}", session.getId(), exception);
            }
            if (!now.isBefore(session.getEndsAt())) {
                session.complete();
            }
        }
        for (TrackingSession session : sessions.findByAlertsEnabledTrueAndFinalAlertSentFalseAndEndsAtLessThanEqual(now)) {
            try {
                send(session, "Session time reached",
                        session.getStudent().getName() + "'s configured session time has ended.");
                session.markFinalAlertSent();
            } catch (MailException exception) {
                log.error("Could not send the final session alert for session {}", session.getId(), exception);
            }
        }
    }

    private void send(TrackingSession session, String subject, String message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        if (!from.isBlank()) {
            mail.setFrom(from);
        }
        mail.setTo(session.getStudent().getTutors().stream().map(tutor -> tutor.getEmail()).toArray(String[]::new));
        mail.setSubject(subject);
        mail.setText(message);
        mailSender.send(mail);
        log.info("Sent '{}' alert for student {}", subject, session.getStudent().getId());
    }
}
