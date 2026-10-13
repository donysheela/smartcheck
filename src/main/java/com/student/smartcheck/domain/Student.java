package com.student.smartcheck.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Min(1)
    @Column(nullable = false)
    private int sessionMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Gender gender;

    @Column(nullable = false)
    private boolean archived;

    @ManyToMany
    @JoinTable(name = "student_tutors",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "tutor_id"))
    private Set<Tutor> tutors = new HashSet<>();

    protected Student() {
    }

    public Student(String name, int sessionMinutes, Gender gender, Set<Tutor> tutors) {
        this.name = name;
        this.sessionMinutes = sessionMinutes;
        this.gender = gender;
        this.tutors = new HashSet<>(tutors);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getSessionMinutes() { return sessionMinutes; }
    public Gender getGender() { return gender; }
    public String getGenmoji() { return gender == Gender.FEMALE ? "👧" : "👦"; }
    public boolean isArchived() { return archived; }
    public Set<Tutor> getTutors() { return tutors; }

    public void update(String name, int sessionMinutes, Gender gender, Set<Tutor> tutors) {
        this.name = name;
        this.sessionMinutes = sessionMinutes;
        this.gender = gender;
        this.tutors.clear();
        this.tutors.addAll(tutors);
    }

    public void archive() { archived = true; }
    public void removeTutor(Tutor tutor) { tutors.remove(tutor); }
}
