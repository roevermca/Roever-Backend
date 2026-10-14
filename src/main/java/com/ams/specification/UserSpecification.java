package com.ams.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.ams.model.Role;
import com.ams.model.User;

import jakarta.persistence.criteria.Predicate;

public class UserSpecification {

    public static Specification<User> filter(
            Role role,
            String departmentId,
            Boolean active) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (departmentId != null && !departmentId.isBlank()) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId.trim()));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
