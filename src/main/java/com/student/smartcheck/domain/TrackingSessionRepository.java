package com.student.smartcheck.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface TrackingSessionRepository extends JpaRepository<TrackingSession, Long> {
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByActiveTrue();
    @EntityGraph(attributePaths = "student")
    List<TrackingSession> findByActiveFalseAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
            Instant start, Instant end);
    long countByActiveFalseAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Instant start, Instant end);
    long countByActiveTrueAndEndsAtGreaterThanEqualAndEndsAtLessThanEqual(
            Instant start, Instant end);
    long countByActiveTrueAndEndsAtAfter(Instant now);
    @EntityGraph(attributePaths = {"student", "student.tutors"})
    List<TrackingSession> findByAlertsEnabledTrueAndFinalAlertSentFalseAndEndsAtLessThanEqual(Instant endsAt);
    Optional<TrackingSession> findFirstByStudentIdAndActiveTrue(Long studentId);
    @Override
    @EntityGraph(attributePaths = "student")
    Page<TrackingSession> findAll(Pageable pageable);
    @EntityGraph(attributePaths = "student")
    @Query(value = """
            select session from TrackingSession session
            order by
                case when session.active = true and session.endsAt > CURRENT_TIMESTAMP then 0 else 1 end,
                case when session.active = true and session.endsAt > CURRENT_TIMESTAMP
                     then session.endsAt else null end asc,
                session.startedAt desc, session.id desc
            """,
            countQuery = "select count(session) from TrackingSession session")
    Page<TrackingSession> findAllForTutorSessions(Pageable pageable);
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
