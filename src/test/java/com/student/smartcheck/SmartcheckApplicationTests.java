package com.student.smartcheck;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.student.smartcheck.domain.Gender;
import com.student.smartcheck.domain.TrackingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartcheck-test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.reminders.enabled=false"
})
class SmartcheckApplicationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private com.student.smartcheck.application.SmartcheckService service;

    @Autowired
    private TrackingSessionRepository sessions;

    @Test
    void contextLoads() {
    }

    @Test
    void rejectsInvalidNamesEmailsAndSessionLengths() {
        assertThatThrownBy(() -> service.addTutor("Tutor1", "valid@example.test", Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letters and spaces");
        assertThatThrownBy(() -> service.addTutor("Valid Tutor", "not-an-email", Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid tutor email");
        assertThatThrownBy(() -> service.addStudent("Student!", 45, Gender.FEMALE, java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letters and spaces");
        assertThatThrownBy(() -> service.addStudent("Valid Student", 481, Gender.FEMALE, java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 480");
    }

    @Test
    void rejectsDuplicateStudentAndTutorNamesIgnoringCase() {
        service.addTutor("Case Check Tutor", "case-check@example.test", Gender.FEMALE);
        service.addStudent("Case Check Student", 45, Gender.MALE, java.util.Set.of());

        assertThatThrownBy(() -> service.addTutor("case check tutor", "another@example.test", Gender.MALE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tutor with this name already exists");
        assertThatThrownBy(() -> service.addStudent("case check student", 45, Gender.FEMALE, java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("student with this name already exists");
    }

    @Test
    void rendersHomeTutorAndStudentPages() throws Exception {
        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> homePage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(homePage.statusCode()).isBetween(200, 299);
        assertThat(homePage.body()).contains("data-active-sessions", "/api/active-sessions");
        HttpResponse<String> tutorPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tutorPage.statusCode()).isBetween(200, 299);
        assertThat(tutorPage.body()).contains("name=\"gender\"", "value=\"FEMALE\"", "value=\"MALE\"",
                "href=\"/tutor/students\"", "href=\"/tutor/sessions\"", "data-active-sessions",
                "data-session-completions-url=\"/api/session-completions\"",
                "Completed sessions today", "pattern=\"[\\p{L} ]+\"", "maxlength=\"100\"",
                "type=\"email\"", "maxlength=\"254\"", "step=\"1\"")
                .doesNotContain("Total sessions");
        HttpResponse<String> studentsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/students")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(studentsPage.statusCode()).isBetween(200, 299);
        assertThat(studentsPage.body()).contains("Students", "data-active-sessions");
        HttpResponse<String> sessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sessionsPage.statusCode()).isBetween(200, 299);
        assertThat(sessionsPage.body()).contains("Recent sessions", "data-active-sessions");
        HttpResponse<String> reportsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/reports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(reportsPage.statusCode()).isBetween(200, 299);
        assertThat(reportsPage.body()).contains("data-active-sessions");
        HttpResponse<byte[]> export = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/reports/export")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(export.statusCode()).isBetween(200, 299);
        assertThat(export.headers().firstValue("Content-Type").orElse(""))
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(export.headers().firstValue("Content-Disposition").orElse(""))
                .contains("smartcheck-report-");
        assertThat(export.body()).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(export.body()))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Sessions")).isNotNull();
        }
        HttpResponse<String> studentPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/student?query=missing")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(studentPage.statusCode()).isBetween(200, 299);
        assertThat(studentPage.body()).contains("data-active-sessions");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void activeSessionCountEndpointReturnsCurrentCount() throws Exception {
        service.addTutor("Live Count Tutor", "live-count@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("live-count@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Live Count Student", 45, Gender.MALE, java.util.Set.of(tutorId));
        Long studentId = service.searchStudents("Live Count Student").get(0).getId();
        long expectedCount = service.activeSessionCount() + 1;
        service.startSession(studentId);

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/active-sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).isEqualTo("{\"count\":" + expectedCount + "}");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void sessionCompletionsEndpointReturnsStudentName() throws Exception {
        service.addTutor("Completion Alert Tutor", "completion-alert@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("completion-alert@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Completion Alert Student", 45, Gender.MALE, java.util.Set.of(tutorId));
        Long studentId = service.searchStudents("Completion Alert Student").get(0).getId();
        var session = service.startSession(studentId);
        service.stopSession(session.getId());

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/session-completions"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("\"studentName\":\"Completion Alert Student\"",
                "\"todayCount\":1");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void expiredActiveSessionIsIncludedAsCompletedImmediately() throws Exception {
        service.addTutor("Instant Completion Tutor", "instant-completion@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("instant-completion@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Instant Completion Student", 45, Gender.MALE, java.util.Set.of(tutorId));
        Long studentId = service.searchStudents("Instant Completion Student").get(0).getId();
        var session = service.startSession(studentId);
        ReflectionTestUtils.setField(session, "endsAt", Instant.now().minusSeconds(1));
        sessions.save(session);
        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/session-completions"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("\"studentName\":\"Instant Completion Student\"",
                "\"todayCount\":1");
    }

    private int getStatus(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void createsStudentAndStartsConfiguredSession() throws Exception {
        service.addTutor("Avery Tutor", "avery@example.test", Gender.FEMALE);
        var tutor = service.tutors().stream()
                .filter(item -> item.getEmail().equals("avery@example.test"))
                .findFirst().orElseThrow();
        Long tutorId = tutor.getId();
        assertThat(tutor.getGender()).isEqualTo(Gender.FEMALE);
        service.addStudent("Jordan Student", 45, Gender.MALE, java.util.Set.of(tutorId));

        var student = service.searchStudents("jordan").get(0);
        var session = service.startSession(student.getId());
        Instant originallyScheduledEnd = service.session(session.getId()).getEndsAt();

        assertThat(session.getEndsAt()).isAfter(session.getStartedAt());
        assertThat(session.getStudent().getSessionMinutes()).isEqualTo(45);
        assertThat(session.getStudent().getGender()).isEqualTo(Gender.MALE);
        assertThat(session.getStudent().getGenmoji()).isEqualTo("👦");
        assertThat(service.activeSessionCount()).isEqualTo(1);

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> extendResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions/" + session.getId()
                                + "/add-time?minutes=15"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(extendResponse.statusCode()).isBetween(300, 399);
        var extendedSession = service.session(session.getId());
        assertThat(extendedSession.getEndsAt()).isEqualTo(originallyScheduledEnd.plusSeconds(15 * 60L));
        assertThat(extendedSession.getAddedMinutes()).isEqualTo(15);

        HttpResponse<byte[]> export = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/reports/export?fromDate="
                        + LocalDate.now() + "&toDate=" + LocalDate.now())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(export.statusCode()).isBetween(200, 299);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(export.body()))) {
            var sessionRow = workbook.getSheet("Sessions").getRow(1);
            assertThat(sessionRow.getCell(0).getStringCellValue())
                    .isEqualTo("Jordan Student");
            assertThat(sessionRow.getCell(2).getStringCellValue())
                    .containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
            assertThat(sessionRow.getCell(3).getStringCellValue())
                    .containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
            assertThat(sessionRow.getCell(5).getNumericCellValue()).isEqualTo(15);
        }

        String timestampPattern = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}";
        HttpResponse<String> tutorPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tutorPage.body()).contains(">Total students</span>", "Completed sessions today");
        HttpResponse<String> sessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> reportsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/reports?fromDate="
                        + LocalDate.now() + "&toDate=" + LocalDate.now())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sessionsPage.body()).containsPattern(timestampPattern)
                .contains("data-session-timer", "In progress", "data-in-progress=\"true\"", "Add minutes");
        assertThat(reportsPage.body()).containsPattern(timestampPattern);
        assertThat(reportsPage.body()).contains("Ended", "Length (min)", "Added (min)", "15");

        HttpResponse<String> studentPage = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/student?query=Jordan%20Student&session=" + session.getId()))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(studentPage.statusCode()).isBetween(200, 299);
        assertThat(studentPage.body()).contains("Jordan Student", "countdown");
    }

    @Test
    void stopsInProgressSessionFromTutorSessionsPage() throws Exception {
        service.addTutor("Stop Session Tutor", "stop-session@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(item -> item.getEmail().equals("stop-session@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Stop Session Student", 45, Gender.MALE, java.util.Set.of(tutorId));
        Long studentId = service.searchStudents("Stop Session Student").get(0).getId();
        var session = service.startSession(studentId);

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> sessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sessionsPage.body()).contains("In-progress sessions", "Completed sessions",
                "Start time", "End time", "Stop Session Student", ">Stop</button>");
        assertThat(sessionsPage.body().indexOf("data-sessions-list=\"in-progress\""))
                .isLessThan(sessionsPage.body().indexOf("Stop Session Student"));
        assertThat(sessionsPage.body().indexOf("Stop Session Student"))
                .isLessThan(sessionsPage.body().indexOf("data-sessions-list=\"completed\""));

        HttpResponse<String> stopResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions/" + session.getId() + "/stop"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(stopResponse.statusCode()).isBetween(300, 399);
        var stoppedSession = service.session(session.getId());
        assertThat(stoppedSession.isActive()).isFalse();
        assertThat(stoppedSession.getCompletedAt()).isNotNull();
        assertThat(service.activeSessionCount()).isZero();

        HttpResponse<String> completedSessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(completedSessionsPage.body()).contains("Stop Session Student", "Completed", "End time")
                .doesNotContain(">Stop</button>");
        assertThat(completedSessionsPage.body().indexOf("data-sessions-list=\"completed\""))
                .isLessThan(completedSessionsPage.body().indexOf("Stop Session Student"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void paginatesStudentsAndSessionsTwentyPerPage() throws Exception {
        service.addTutor("Pagination Tutor", "pagination@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(item -> item.getEmail().equals("pagination@example.test"))
                .findFirst().orElseThrow().getId();
        for (int i = 0; i < 21; i++) {
            String studentName = "Paging Student " + (char) ('A' + i);
            service.addStudent(studentName, 45, Gender.MALE, java.util.Set.of(tutorId));
            Long studentId = service.searchStudents(studentName).get(0).getId();
            service.startSession(studentId);
        }

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> firstStudentsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl
                        + "/tutor/students?name=Paging%20Student")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> secondStudentsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl
                        + "/tutor/students?name=Paging%20Student&page=1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(firstStudentsPage.body()).contains("Paging Student A", "Paging Student T",
                        "Page <span>1</span> of <span>2</span>")
                .doesNotContain("Paging Student U");
        assertThat(secondStudentsPage.body()).contains("Paging Student U",
                        "Page <span>2</span> of <span>2</span>")
                .doesNotContain("Paging Student T");

        HttpResponse<String> sortedStudents = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl
                        + "/tutor/students?name=Paging%20Student&sort=desc&view=table")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sortedStudents.statusCode()).isBetween(200, 299);
        assertThat(sortedStudents.body()).contains("data-student-results=\"table\"",
                        "data-student-view=\"table\"", "Sort by name", "Search by name",
                        "Paging Student U", "Page <span>1</span> of <span>2</span>")
                .doesNotContain("Paging Student A");
        assertThat(sortedStudents.body().indexOf("Paging Student U"))
                .isLessThan(sortedStudents.body().indexOf("Paging Student T"));

        HttpResponse<String> firstSessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> secondSessionsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions?page=1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(firstSessionsPage.body()).contains("Paging Student A", "Paging Student T",
                        "Page <span>1</span> of <span>2</span>")
                .doesNotContain("Paging Student U");
        assertThat(firstSessionsPage.body().indexOf("Paging Student A"))
                .isLessThan(firstSessionsPage.body().indexOf("Paging Student T"));
        assertThat(secondSessionsPage.body()).contains("Paging Student U",
                        "Page <span>2</span> of <span>2</span>")
                .doesNotContain("Paging Student T");
    }

    @Test
    void deletingStudentKeepsSessionHistoryAndRemovesStudentFromActiveViews() throws Exception {
        service.addTutor("Casey Tutor", "casey@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().get(0).getId();
        service.addStudent("Casey Student", 45, Gender.FEMALE, java.util.Set.of(tutorId));
        var student = service.searchStudents("Casey").get(0);
        var session = service.startSession(student.getId());

        service.archiveStudent(student.getId());

        assertThat(service.students()).noneMatch(activeStudent -> activeStudent.getId().equals(student.getId()));
        assertThat(service.searchStudents("Casey")).isEmpty();
        assertThat(service.studentCount()).isEqualTo(service.students().size());
        assertThat(service.session(session.getId()).getStudent().getName()).isEqualTo("Casey Student");
        assertThat(service.session(session.getId()).getCompletedAt()).isNotNull();
        assertThat(service.activeSessionCount()).isZero();

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> sessionsPage = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/sessions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(sessionsPage.body()).contains("Casey Student", "bg-rose-50", "Completed", "End time");
    }

    @Test
    void deletingTutorRemovesAssignmentsAndPreservesStudent() {
        service.addTutor("Morgan Tutor", "morgan@example.test", Gender.MALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("morgan@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Morgan Student", 45, Gender.MALE, java.util.Set.of(tutorId));
        Long studentId = service.searchStudents("Morgan").get(0).getId();

        service.deleteTutor(tutorId);

        assertThat(service.tutors()).noneMatch(tutor -> tutor.getId().equals(tutorId));
        var student = service.students().stream()
                .filter(activeStudent -> activeStudent.getId().equals(studentId)).findFirst().orElseThrow();
        assertThat(student.getTutors()).isEmpty();
    }

    @Test
    void updatesStudentDetailsAndAssignedTutors() {
        service.addTutor("Original Tutor", "original@example.test", Gender.FEMALE);
        service.addTutor("Replacement Tutor", "replacement@example.test", Gender.MALE);
        Long originalTutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("original@example.test"))
                .findFirst().orElseThrow().getId();
        Long replacementTutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("replacement@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Before Update", 45, Gender.FEMALE, java.util.Set.of(originalTutorId));
        Long studentId = service.searchStudents("Before Update").get(0).getId();

        service.updateStudent(studentId, "After Update", 60, Gender.MALE,
                java.util.Set.of(replacementTutorId));

        var updatedStudent = service.searchStudents("After Update").get(0);
        assertThat(updatedStudent.getName()).isEqualTo("After Update");
        assertThat(updatedStudent.getSessionMinutes()).isEqualTo(60);
        assertThat(updatedStudent.getGender()).isEqualTo(Gender.MALE);
        assertThat(updatedStudent.getTutors()).extracting("id").containsExactly(replacementTutorId);
    }

    @Test
    void editingStudentChecksItsAssignedTutor() throws Exception {
        service.addTutor("Assigned Tutor", "assigned@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("assigned@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Assigned Student", 45, Gender.FEMALE, java.util.Set.of(tutorId));

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/students")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).containsPattern(
                "name=\"tutorIds\" value=\"" + tutorId + "\"[^>]*checked=\"checked\"");
    }

    @Test
    void addingStudentWithoutTutorIsAllowed() throws Exception {
        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/students"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "name=No+Tutor+Student&sessionMinutes=45&gender=FEMALE"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(300, 399);
        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(baseUrl + "/tutor/students");
        assertThat(service.students()).anySatisfy(student -> {
            assertThat(student.getName()).isEqualTo("No Tutor Student");
            assertThat(student.getTutors()).isEmpty();
        });
    }

    @Test
    void addingStudentSelectsAllAvailableTutorsByDefault() throws Exception {
        service.addTutor("Default Tutor One", "default-one@example.test", Gender.FEMALE);
        service.addTutor("Default Tutor Two", "default-two@example.test", Gender.MALE);

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        long tutorCheckboxes = java.util.regex.Pattern.compile("name=\"tutorIds\" value=\"\\d+\"")
                .matcher(response.body()).results().count();
        long checkedTutorCheckboxes = java.util.regex.Pattern.compile(
                        "name=\"tutorIds\" value=\"\\d+\"[^>]*\\schecked(?:\\s|>)")
                .matcher(response.body()).results().count();
        assertThat(tutorCheckboxes).isGreaterThanOrEqualTo(2);
        assertThat(checkedTutorCheckboxes).isEqualTo(tutorCheckboxes);
    }

    @Test
    void suggestsMatchingStudentsAfterThreeCharacters() throws Exception {
        service.addTutor("Suggestion Tutor", "suggestion@example.test", Gender.FEMALE);
        Long tutorId = service.tutors().stream()
                .filter(tutor -> tutor.getEmail().equals("suggestion@example.test"))
                .findFirst().orElseThrow().getId();
        service.addStudent("Suggestion Student", 45, Gender.FEMALE, java.util.Set.of(tutorId));

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> shortQuery = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/student/suggestions?query=Su")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> matchingQuery = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/student/suggestions?query=Suggest")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(shortQuery.statusCode()).isBetween(200, 299);
        assertThat(shortQuery.body()).isEqualTo("[]");
        assertThat(matchingQuery.statusCode()).isBetween(200, 299);
        assertThat(matchingQuery.body()).contains("Suggestion Student");
    }
}
