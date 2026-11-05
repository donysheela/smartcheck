package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
import com.student.smartcheck.domain.Student;
import com.student.smartcheck.domain.TrackingSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {
    private final SmartcheckService service;

    public StudentController(SmartcheckService service) {
        this.service = service;
    }

    @GetMapping
    public String studentPage(@RequestParam(defaultValue = "") String query,
                              @RequestParam(required = false) Long selectedStudentId,
                              @RequestParam(required = false) Long session,
                              Model model) {
        model.addAttribute("query", query);
        model.addAttribute("selectedStudentId", selectedStudentId);
        List<Student> results = query.isBlank() ? List.of() : service.searchStudents(query);
        if (selectedStudentId != null) {
            results = results.stream()
                    .filter(student -> student.getId().equals(selectedStudentId))
                    .toList();
        }
        model.addAttribute("results", results);
        if (session != null) {
            model.addAttribute("activeSession", service.session(session));
        }
        return "student";
    }

    @GetMapping("/suggestions")
    @ResponseBody
    public List<StudentSuggestion> suggestions(@RequestParam(defaultValue = "") String query) {
        if (query.trim().length() < 3) {
            return List.of();
        }
        return service.searchStudents(query).stream()
                .map(student -> new StudentSuggestion(student.getId(), student.getName()))
                .toList();
    }

    public record StudentSuggestion(Long id, String name) {
    }

    @PostMapping("/start")
    public String start(@RequestParam Long studentId, RedirectAttributes redirect) {
        try {
            TrackingSession session = service.startSession(studentId);
            return "redirect:/student?query=" + java.net.URLEncoder.encode(
                    session.getStudent().getName(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&session=" + session.getId();
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/student";
        }
    }
}
