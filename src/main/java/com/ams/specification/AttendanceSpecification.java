package com.ams.specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.ams.model.Attendance;
import com.ams.model.AttendanceStatus;

import jakarta.persistence.criteria.Predicate;

public class AttendanceSpecification {

    public static Specification<Attendance> filter(
            String studentId,
            List<String> studentIds,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            Integer period,
            AttendanceStatus status) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (studentId != null && !studentId.isBlank()) {
                predicates.add(cb.equal(root.get("studentId"), studentId.trim()));
            } else if (studentIds != null && !studentIds.isEmpty()) {
                predicates.add(root.get("studentId").in(studentIds));
            }

            if (date != null) {
                predicates.add(cb.equal(root.get("date"), date));
            } else {
                if (startDate != null && endDate != null) {
                    predicates.add(cb.between(root.get("date"), startDate, endDate));
                } else if (startDate != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("date"), startDate));
                } else if (endDate != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("date"), endDate));
                }
            }

            if (period != null) {
                predicates.add(cb.equal(root.get("period"), period));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
