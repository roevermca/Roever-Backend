package com.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ams.model.Department;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {

    Optional<Department> findByCode(String code);

    Optional<Department> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByCodeAndIdNot(String code, String id);

    boolean existsByNameAndIdNot(String name, String id);

    List<Department> findByActiveTrue();
}
