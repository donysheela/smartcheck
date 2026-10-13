package com.student.smartcheck.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    @EntityGraph(attributePaths = "tutors")
    List<Student> findTop20ByArchivedFalseAndNameContainingIgnoreCaseOrderByNameAsc(String name);
    @EntityGraph(attributePaths = "tutors")
    List<Student> findAllByArchivedFalseOrderByNameAsc();
    @EntityGraph(attributePaths = "tutors")
    List<Student> findByTutors_Id(Long tutorId);
    long countByArchivedFalse();
}
