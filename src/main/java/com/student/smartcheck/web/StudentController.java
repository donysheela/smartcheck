package com.student.smartcheck.web;

import com.student.smartcheck.application.SmartcheckService;
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
                              @RequestParam(required = false) Long session,
                              Model model) {
        model.addAttribute("query", query);
        model.addAttribute("results", query.isBlank() ? java.util.List.of() : service.searchStudents(query));
        if (session != null) {
            model.addAttribute("activeSession", service.session(session));
        }
        return "student";
    }

    @GetMapping("/suggestions")
    @ResponseBody
    public List<String> suggestions(@RequestParam(defaultValue = "") String query) {
        if (query.trim().length() < 3) {
            return List.of();
        }
        return service.searchStudents(query).stream()
                .map(student -> student.getName())
                .distinct()
                .toList();
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
