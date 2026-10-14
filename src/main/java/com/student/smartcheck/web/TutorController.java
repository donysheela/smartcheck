package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
import com.student.smartcheck.domain.TrackingSession;
import com.student.smartcheck.domain.Gender;
import com.student.smartcheck.domain.Student;
import com.student.smartcheck.domain.Tutor;
import com.student.smartcheck.util.DateTimeDisplay;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Set;

@Controller
@RequestMapping("/tutor")
public class TutorController {
    private final SmartcheckService service;

    public TutorController(SmartcheckService service) {
        this.service = service;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("tutors", service.tutors());
        model.addAttribute("students", service.students());
        model.addAttribute("sessions", service.recentSessions().stream().map(this::sessionDisplay).toList());
        model.addAttribute("activeCount", service.activeSessionCount());
        return "tutor";
    }

    @GetMapping("/reports")
    public String reports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        addReportAttributes(model, buildReport(fromDate, toDate));
        return "tutor-reports";
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate)
            throws IOException {
        ReportData report = buildReport(fromDate, toDate);
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Summary");
            addSummaryRow(summary, 0, "Metric", "Value");
            addSummaryRow(summary, 1, "Session date range", report.rangeLabel());
            addSummaryRow(summary, 2, "Total students", report.studentCount());
            addSummaryRow(summary, 3, "Active sessions now", report.activeCount());
            addSummaryRow(summary, 4, "Sessions started in range", report.sessions().size());
            addSummaryRow(summary, 5, "Completed sessions in range", report.completedCount());
            addSummaryRow(summary, 6, "Students with sessions in range", report.participatingStudentCount());
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            Sheet sessionSheet = workbook.createSheet("Sessions");
            Row header = sessionSheet.createRow(0);
            header.createCell(0).setCellValue("Student");
            header.createCell(1).setCellValue("Gender");
            header.createCell(2).setCellValue("Started");
            header.createCell(3).setCellValue("Scheduled end");
            header.createCell(4).setCellValue("Session length (minutes)");
            header.createCell(5).setCellValue("Status");
            for (int i = 0; i < report.sessions().size(); i++) {
                TrackingSession session = report.sessions().get(i);
                Row row = sessionSheet.createRow(i + 1);
                row.createCell(0).setCellValue(session.getStudent().getName());
                row.createCell(1).setCellValue(session.getStudent().getGender().toString());
                row.createCell(2).setCellValue(DateTimeDisplay.format(session.getStartedAt()));
                row.createCell(3).setCellValue(DateTimeDisplay.format(session.getEndsAt()));
                row.createCell(4).setCellValue(session.getStudent().getSessionMinutes());
                row.createCell(5).setCellValue(session.isActive() ? "In progress" : "Completed");
            }
            for (int column = 0; column < 6; column++) {
                sessionSheet.autoSizeColumn(column);
            }
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"smartcheck-report-" + report.fileRange() + ".xlsx\"")
                .body(workbookBytes);
    }

    private ReportData buildReport(LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate effectiveToDate = toDate == null ? today : toDate;
        if (effectiveToDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To date cannot be after today.");
        }
        if (fromDate != null && fromDate.isAfter(effectiveToDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must be on or before to date.");
        }
        Instant start = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant end = effectiveToDate.plusDays(1).atStartOfDay(zone).toInstant();
        var sessions = service.sessionsForReport(start, end);
        long completedCount = sessions.stream().filter(session -> !session.isActive()).count();
        long participatingStudentCount = sessions.stream()
                .map(session -> session.getStudent().getId()).distinct().count();
        String rangeLabel = (fromDate == null ? "All dates" : fromDate.toString())
                + " through " + effectiveToDate;
        String fileRange = (fromDate == null ? "all" : fromDate.toString()) + "-to-" + effectiveToDate;
        return new ReportData(today, fromDate, effectiveToDate, rangeLabel, fileRange,
                service.studentCount(), service.activeSessionCount(),
                sessions, completedCount, participatingStudentCount);
    }

    private void addReportAttributes(Model model, ReportData report) {
        model.addAttribute("studentCount", report.studentCount());
        model.addAttribute("activeCount", report.activeCount());
        model.addAttribute("today", report.today());
        model.addAttribute("fromDate", report.fromDate());
        model.addAttribute("toDate", report.toDate());
        model.addAttribute("rangeLabel", report.rangeLabel());
        model.addAttribute("sessions", report.sessions().stream().map(this::sessionDisplay).toList());
        model.addAttribute("sessionCount", report.sessions().size());
        model.addAttribute("completedCount", report.completedCount());
        model.addAttribute("participatingStudentCount", report.participatingStudentCount());
    }

    private void addSummaryRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void addSummaryRow(Sheet sheet, int rowIndex, String label, long value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private SessionDisplay sessionDisplay(TrackingSession session) {
        return new SessionDisplay(session, DateTimeDisplay.format(session.getStartedAt()));
    }

    private record ReportData(LocalDate today, LocalDate fromDate, LocalDate toDate,
                              String rangeLabel, String fileRange, long studentCount, long activeCount,
                              java.util.List<TrackingSession> sessions, long completedCount,
                              long participatingStudentCount) {
    }

    private record SessionDisplay(TrackingSession session, String startedAt) {
    }

    @PostMapping("/tutors")
    public String addTutor(@RequestParam String name, @RequestParam String email,
                           @RequestParam Gender gender,
                           RedirectAttributes redirect) {
        try {
            if (name.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Enter a tutor name and a valid email address.");
            }
            service.addTutor(name, email, gender);
            redirect.addFlashAttribute("success", "Tutor added.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/tutor";
    }

    @PostMapping("/students")
    public String addStudent(@RequestParam String name, @RequestParam int sessionMinutes,
                             @RequestParam Gender gender,
                             @RequestParam(required = false) Set<Long> tutorIds,
                             RedirectAttributes redirect) {
        try {
            service.addStudent(name, sessionMinutes, gender, tutorIds);
            redirect.addFlashAttribute("success", "Student added.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/tutor";
    }

    @PostMapping("/students/{studentId}")
    public String updateStudent(@PathVariable Long studentId, @RequestParam String name,
                                @RequestParam int sessionMinutes, @RequestParam Gender gender,
                                @RequestParam(required = false) Set<Long> tutorIds,
                                RedirectAttributes redirect) {
        try {
            service.updateStudent(studentId, name, sessionMinutes, gender, tutorIds);
            redirect.addFlashAttribute("success", "Student updated.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/tutor";
    }

    @PostMapping("/students/{studentId}/delete")
    public String deleteStudent(@PathVariable Long studentId, RedirectAttributes redirect) {
        try {
            service.archiveStudent(studentId);
            redirect.addFlashAttribute("success",
                    "Student removed from the active list. Their session history has been kept.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/tutor";
    }

    @PostMapping("/tutors/{tutorId}/delete")
    public String deleteTutor(@PathVariable Long tutorId, RedirectAttributes redirect) {
        try {
            service.deleteTutor(tutorId);
            redirect.addFlashAttribute("success",
                    "Tutor deleted and removed from student assignments. Session history has been kept.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/tutor";
    }
}
