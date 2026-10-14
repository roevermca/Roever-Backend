package com.ams.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.ams.model.ProgramType;
import com.ams.model.Student;

import jakarta.persistence.criteria.Predicate;

public class StudentSpecification {

    public static Specification<Student> filter(
            List<String> departmentIds,
            List<String> courseIds,
            ProgramType programType,
            Integer year,
            String section,
            String search,
            String studentId,
            String email) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (studentId != null && !studentId.isBlank()) {
                predicates.add(cb.equal(root.get("id"), studentId.trim()));
            }

            if (email != null && !email.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("email")), email.trim().toLowerCase()));
            }

            if (departmentIds != null && !departmentIds.isEmpty()) {
                predicates.add(root.get("departmentId").in(departmentIds));
            }

            if (courseIds != null && !courseIds.isEmpty()) {
                predicates.add(root.get("courseId").in(courseIds));
            }

            if (programType != null) {
                predicates.add(cb.equal(root.get("programType"), programType));
            }

            if (year != null) {
                predicates.add(cb.equal(root.get("year"), year));
            }

            if (section != null && !section.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("section")), section.trim().toUpperCase()));
            }

            if (search != null && !search.isBlank()) {
                String term = search.trim().toLowerCase();
                Predicate rollNoMatch = cb.like(cb.lower(root.get("rollNo")), term + "%");
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), "%" + term + "%");
                predicates.add(cb.or(rollNoMatch, nameMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
