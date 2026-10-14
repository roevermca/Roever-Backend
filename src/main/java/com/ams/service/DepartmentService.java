package com.ams.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ams.dto.DepartmentRequest;
import com.ams.dto.DepartmentResponse;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Department;
import com.ams.repository.DepartmentRepository;
import com.ams.security.SecurityUtils;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final MetadataCacheService metadataCacheService;

    public DepartmentService(DepartmentRepository departmentRepository, MetadataCacheService metadataCacheService) {
        this.departmentRepository = departmentRepository;
        this.metadataCacheService = metadataCacheService;
    }

    public List<DepartmentResponse> getDepartments(Boolean activeOnly) {
        List<Department> departments;
        if (Boolean.TRUE.equals(activeOnly)) {
            departments = departmentRepository.findByActiveTrue();
        } else {
            departments = departmentRepository.findAll(Sort.by("name").ascending());
        }
        return departments.stream()
                .map(DepartmentResponse::fromEntity)
                .toList();
    }

    public DepartmentResponse getDepartmentById(String id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + id));
        return DepartmentResponse.fromEntity(department);
    }

    public DepartmentResponse createDepartment(DepartmentRequest request) {
        SecurityUtils.checkDepartmentManagementAccess();

        String code = request.getCode().trim().toUpperCase();
        if (departmentRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Department with code " + code + " already exists");
        }

        String name = request.getName().trim();
        if (departmentRepository.existsByName(name)) {
            throw new DuplicateResourceException("Department with name " + name + " already exists");
        }

        Department department = new Department(
                name,
                code,
                request.getActive() != null ? request.getActive() : true
        );

        Department saved = departmentRepository.save(department);
        if (metadataCacheService != null) {
            metadataCacheService.evictDepartment(saved.getId());
        }
        return DepartmentResponse.fromEntity(saved);
    }

    public DepartmentResponse updateDepartment(String id, DepartmentRequest request) {
        SecurityUtils.checkDepartmentManagementAccess();

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + id));

        String newCode = request.getCode().trim().toUpperCase();
        if (!department.getCode().equalsIgnoreCase(newCode) && departmentRepository.existsByCodeAndIdNot(newCode, id)) {
            throw new DuplicateResourceException("Department with code " + newCode + " already exists");
        }

        String newName = request.getName().trim();
        if (!department.getName().equalsIgnoreCase(newName) && departmentRepository.existsByNameAndIdNot(newName, id)) {
            throw new DuplicateResourceException("Department with name " + newName + " already exists");
        }

        department.setName(newName);
        department.setCode(newCode);
        if (request.getActive() != null) {
            department.setActive(request.getActive());
        }

        Department updated = departmentRepository.save(department);
        if (metadataCacheService != null) {
            metadataCacheService.evictDepartment(id);
        }
        return DepartmentResponse.fromEntity(updated);
    }

    public void deleteDepartment(String id) {
        SecurityUtils.checkDepartmentManagementAccess();

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + id));
        departmentRepository.delete(department);
        if (metadataCacheService != null) {
            metadataCacheService.evictDepartment(id);
        }
    }
}
