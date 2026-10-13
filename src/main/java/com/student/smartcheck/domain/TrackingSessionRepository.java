package com.student.smartcheck.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface TrackingSessionRepository extends JpaRepository<TrackingSession, Long> {
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByActiveTrue();
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByAlertsEnabledTrueAndFinalAlertSentFalseAndEndsAtLessThanEqual(Instant endsAt);
    Optional<TrackingSession> findFirstByStudentIdAndActiveTrue(Long studentId);
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findTop20ByOrderByStartedAtDesc();
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
            Instant start, Instant end);
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByStartedAtLessThanOrderByStartedAtDesc(Instant end);
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByStartedAtGreaterThanEqualOrderByStartedAtDesc(Instant start);
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findAllByOrderByStartedAtDesc();
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    Optional<TrackingSession> findWithStudentById(Long id);
}
