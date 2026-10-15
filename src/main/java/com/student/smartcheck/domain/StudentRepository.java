package com.student.smartcheck.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    @EntityGraph(attributePaths = "tutors")
    List<Student> findTop20ByArchivedFalseAndNameContainingIgnoreCaseOrderByNameAsc(String name);
    @EntityGraph(attributePaths = "tutors")
    List<Student> findAllByArchivedFalseOrderByNameAsc();
    Page<Student> findAllByArchivedFalse(Pageable pageable);
    Page<Student> findByArchivedFalseAndNameContainingIgnoreCase(String name, Pageable pageable);
    @EntityGraph(attributePaths = "tutors")
    List<Student> findByTutors_Id(Long tutorId);
    long countByArchivedFalse();
}
