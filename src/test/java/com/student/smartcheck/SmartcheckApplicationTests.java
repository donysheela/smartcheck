package com.student.smartcheck;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.student.smartcheck.domain.Gender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void contextLoads() {
    }

    @Test
    void rendersHomeTutorAndStudentPages() throws Exception {
        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
        HttpClient client = HttpClient.newHttpClient();
        assertThat(getStatus(client, baseUrl + "/")).isBetween(200, 299);
        HttpResponse<String> tutorPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tutorPage.statusCode()).isBetween(200, 299);
        assertThat(tutorPage.body()).contains("name=\"gender\"", "value=\"FEMALE\"", "value=\"MALE\"");
        assertThat(getStatus(client, baseUrl + "/tutor/reports")).isBetween(200, 299);
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
        assertThat(getStatus(client, baseUrl + "/student?query=missing")).isBetween(200, 299);
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

        assertThat(session.getEndsAt()).isAfter(session.getStartedAt());
        assertThat(session.getStudent().getSessionMinutes()).isEqualTo(45);
        assertThat(session.getStudent().getGender()).isEqualTo(Gender.MALE);
        assertThat(session.getStudent().getGenmoji()).isEqualTo("👦");
        assertThat(service.activeSessionCount()).isEqualTo(1);

        String baseUrl = "http://localhost:" + environment.getProperty("local.server.port");
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
        }

        String timestampPattern = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}";
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> tutorPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> reportsPage = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/tutor/reports?fromDate="
                        + LocalDate.now() + "&toDate=" + LocalDate.now())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tutorPage.body()).containsPattern(timestampPattern);
        assertThat(reportsPage.body()).containsPattern(timestampPattern);

        HttpResponse<String> studentPage = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/student?query=Jordan%20Student&session=" + session.getId()))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(studentPage.statusCode()).isBetween(200, 299);
        assertThat(studentPage.body()).contains("Jordan Student", "countdown");
    }

    @Test
    void deletingStudentKeepsSessionHistoryAndRemovesStudentFromActiveViews() {
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
        assertThat(service.activeSessionCount()).isZero();
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
