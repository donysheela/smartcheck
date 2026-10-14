package com.student.smartcheck.application;

import com.student.smartcheck.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SmartcheckService {
    private final TutorRepository tutors;
    private final StudentRepository students;
    private final TrackingSessionRepository sessions;
    private final boolean remindersEnabled;

    public SmartcheckService(TutorRepository tutors, StudentRepository students,
                             TrackingSessionRepository sessions,
                             @Value("${app.reminders.enabled:false}") boolean remindersEnabled) {
        this.tutors = tutors;
        this.students = students;
        this.sessions = sessions;
        this.remindersEnabled = remindersEnabled;
    }

    @Transactional(readOnly = true)
    public List<Tutor> tutors() {
        return tutors.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Student> students() {
        return students.findAllByArchivedFalseOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Page<Student> students(Pageable pageable) {
        Page<Student> page = students.findAllByArchivedFalse(pageable);
        for (Student student : page) {
            student.getTutors().size();
        }
        return page;
    }

    @Transactional(readOnly = true)
    public Page<Student> students(String name, Pageable pageable) {
        Page<Student> page = students.findByArchivedFalseAndNameContainingIgnoreCase(name, pageable);
        for (Student student : page) {
            student.getTutors().size();
        }
        return page;
    }

    @Transactional(readOnly = true)
    public List<Student> searchStudents(String name) {
        return students.findTop20ByArchivedFalseAndNameContainingIgnoreCaseOrderByNameAsc(name.trim());
    }

    @Transactional
    public void addTutor(String name, String email, Gender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("Select a tutor gender.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (tutors.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("A tutor with this email address already exists.");
        }
        tutors.save(new Tutor(name.trim(), normalizedEmail, gender));
    }

    @Transactional
    public void addStudent(String name, int sessionMinutes, Gender gender, Set<Long> tutorIds) {
        validateStudentDetails(name, sessionMinutes, gender);
        Set<Tutor> assignedTutors = findAssignedTutors(tutorIds);
        students.save(new Student(name.trim(), sessionMinutes, gender, assignedTutors));
    }

    @Transactional
    public void updateStudent(Long studentId, String name, int sessionMinutes, Gender gender, Set<Long> tutorIds) {
        validateStudentDetails(name, sessionMinutes, gender);
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student was not found."));
        if (student.isArchived()) {
            throw new IllegalArgumentException("This student is no longer active.");
        }
        student.update(name.trim(), sessionMinutes, gender, findAssignedTutors(tutorIds));
    }

    private void validateStudentDetails(String name, int sessionMinutes, Gender gender) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a student name.");
        }
        if (sessionMinutes < 1 || sessionMinutes > 480) {
            throw new IllegalArgumentException("Session length must be between 1 and 480 minutes.");
        }
        if (gender == null) {
            throw new IllegalArgumentException("Select a student gender.");
        }
    }

    private Set<Tutor> findAssignedTutors(Set<Long> tutorIds) {
        if (tutorIds == null || tutorIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one tutor to receive alerts.");
        }
        Set<Tutor> assignedTutors = tutors.findAllById(tutorIds).stream().collect(Collectors.toSet());
        if (assignedTutors.size() != tutorIds.size()) {
            throw new IllegalArgumentException("One or more selected tutors could not be found.");
        }
        return assignedTutors;
    }

    @Transactional
    public void archiveStudent(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student was not found."));
        if (student.isArchived()) {
            throw new IllegalArgumentException("Student has already been deleted.");
        }
        sessions.findFirstByStudentIdAndActiveTrue(studentId)
                .ifPresent(session -> session.complete(Instant.now()));
        student.archive();
    }

    @Transactional
    public void addSessionMinutes(Long sessionId, int minutes) {
        if (minutes < 1 || minutes > 480) {
            throw new IllegalArgumentException("Additional time must be between 1 and 480 minutes.");
        }
        TrackingSession session = sessions.findWithStudentById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session was not found."));
        Instant now = Instant.now();
        if (!session.isActive() || !session.getEndsAt().isAfter(now)
                || !session.getStartedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now())) {
            throw new IllegalArgumentException("Additional time can only be added to an active session started today.");
        }
        session.addMinutes(minutes);
    }

    @Transactional
    public void deleteTutor(Long tutorId) {
        Tutor tutor = tutors.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("Tutor was not found."));
        for (Student student : students.findByTutors_Id(tutorId)) {
            student.removeTutor(tutor);
        }
        tutors.delete(tutor);
    }

    @Transactional
    public TrackingSession startSession(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student was not found."));
        if (student.isArchived()) {
            throw new IllegalArgumentException("This student is no longer active.");
        }
        if (sessions.findFirstByStudentIdAndActiveTrue(studentId).isPresent()) {
            throw new IllegalArgumentException("This student already has an active timer.");
        }
        Instant now = Instant.now();
        return sessions.save(new TrackingSession(student, now,
                now.plusSeconds(student.getSessionMinutes() * 60L), remindersEnabled));
    }

    @Transactional(readOnly = true)
    public Page<TrackingSession> sessions(Pageable pageable) {
        return sessions.findAllForTutorSessions(pageable);
    }

    @Transactional(readOnly = true)
    public long activeSessionCount() {
        return sessions.findByActiveTrue().size();
    }

    @Transactional(readOnly = true)
    public long studentCount() {
        return students.countByArchivedFalse();
    }

    @Transactional(readOnly = true)
    public long totalSessionCount() {
        return sessions.count();
    }

    @Transactional(readOnly = true)
    public List<TrackingSession> sessionsBetween(Instant start, Instant end) {
        return sessions.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(start, end);
    }

    @Transactional(readOnly = true)
    public List<TrackingSession> sessionsForReport(Instant start, Instant end) {
        if (start == null && end == null) {
            return sessions.findAllByOrderByStartedAtDesc();
        }
        if (start == null) {
            return sessions.findByStartedAtLessThanOrderByStartedAtDesc(end);
        }
        if (end == null) {
            return sessions.findByStartedAtGreaterThanEqualOrderByStartedAtDesc(start);
        }
        return sessions.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(start, end);
    }

    @Transactional(readOnly = true)
    public TrackingSession session(Long id) {
        return sessions.findWithStudentById(id).orElseThrow(() -> new IllegalArgumentException("Session was not found."));
    }
}
