package com.ams.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.ams.model.Course;
import com.ams.model.ProgramType;

import jakarta.persistence.criteria.Predicate;

public class CourseSpecification {

    public static Specification<Course> filter(
            String departmentId,
            ProgramType programType,
            Integer durationYears,
            Boolean active) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (departmentId != null && !departmentId.isBlank()) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId.trim()));
            }

            if (programType != null) {
                predicates.add(cb.equal(root.get("programType"), programType));
            }

            if (durationYears != null) {
                predicates.add(cb.equal(root.get("durationYears"), durationYears));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
