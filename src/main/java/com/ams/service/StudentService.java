package com.ams.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ams.dto.BulkDeleteResponse;
import com.ams.dto.BulkStudentImportResponse;
import com.ams.dto.PageResponse;
import com.ams.dto.StudentRequest;
import com.ams.dto.StudentResponse;
import com.ams.exception.AccessDeniedException;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Course;
import com.ams.model.Department;
import com.ams.model.ProgramType;
import com.ams.model.Role;
import com.ams.model.Student;
import com.ams.model.User;
import com.ams.repository.AttendanceArchiveRepository;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.CourseRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;
import com.ams.security.AuthenticatedUser;
import com.ams.security.SecurityUtils;
import com.ams.specification.StudentSpecification;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceArchiveRepository archiveRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final MetadataCacheService metadataCacheService;

    @Autowired
    public StudentService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            AttendanceRepository attendanceRepository,
            AttendanceArchiveRepository archiveRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            @Autowired(required = false) MetadataCacheService metadataCacheService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.archiveRepository = archiveRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.metadataCacheService = metadataCacheService;
    }

    public StudentService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            AttendanceRepository attendanceRepository,
            AttendanceArchiveRepository archiveRepository) {
        this(studentRepository, userRepository, attendanceRepository, archiveRepository, null, null, null);
    }

    public PageResponse<StudentResponse> getStudents(
            String departmentId,
            String courseId,
            ProgramType programType,
            Integer year,
            String section,
            String search,
            int page,
            int size) {

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        String studentIdFilter = null;
        String emailFilter = null;

        if (currentUser != null) {
            if (currentUser.getRole() == Role.HOD && currentUser.getDepartmentId() != null) {
                // Rule 6: Never trust departmentId sent by frontend. Restrict HOD to own department.
                departmentId = currentUser.getDepartmentId();
            } else if (currentUser.getRole() == Role.STAFF) {
                // STAFF ACCESS CONTROL: If staff is assigned to a course, force restrict to that course!
                if (currentUser.getCourseId() != null && !currentUser.getCourseId().isBlank()) {
                    courseId = currentUser.getCourseId();
                    departmentId = null;
                } else if (currentUser.getDepartmentId() != null && !currentUser.getDepartmentId().isBlank()) {
                    departmentId = currentUser.getDepartmentId();
                }
            } else if (currentUser.getRole() == Role.STUDENT) {
                // Rule 7: Student can only access their own data.
                if (currentUser.getStudentId() != null) {
                    studentIdFilter = currentUser.getStudentId();
                } else if (currentUser.getEmail() != null) {
                    emailFilter = currentUser.getEmail().toLowerCase().trim();
                }
            }
        }

        List<String> deptMatches = (departmentId != null && !departmentId.isBlank())
                ? getMatchingDeptIdentifiers(departmentId)
                : null;
        List<String> courseMatches = (courseId != null && !courseId.isBlank())
                ? getMatchingCourseIdentifiers(courseId)
                : null;

        Specification<Student> spec = StudentSpecification.filter(
                deptMatches,
                courseMatches,
                programType,
                year,
                section,
                search,
                studentIdFilter,
                emailFilter
        );

        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0) ? 20 : Math.min(100, size);

        PageRequest pageRequest = PageRequest.of(validatedPage, validatedSize, Sort.by("rollNo").ascending());
        Page<Student> pagedResult = studentRepository.findAll(spec, pageRequest);

        Map<String, String> deptNames = new HashMap<>();
        Map<String, String> crsNames = new HashMap<>();

        List<StudentResponse> data = pagedResult.getContent().stream()
                .map(s -> StudentResponse.fromEntity(
                        s,
                        resolveDepartmentName(s.getDepartmentId(), deptNames),
                        resolveCourseName(s.getCourseId(), crsNames)
                ))
                .toList();

        return new PageResponse<>(
                data,
                validatedPage,
                validatedSize,
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages()
        );
    }

    public List<String> getMatchingCourseIdentifiers(String courseIdentifier) {
        if (courseIdentifier == null || courseIdentifier.isBlank()) return List.of();
        if (metadataCacheService != null) {
            return metadataCacheService.getMatchingCourseIdentifiers(courseIdentifier);
        }
        String trimmed = courseIdentifier.trim();
        List<String> ids = new ArrayList<>();
        ids.add(trimmed);
        try {
            if (courseRepository != null) {
                courseRepository.findById(trimmed).ifPresent(c -> {
                    if (c.getCode() != null) ids.add(c.getCode());
                    if (c.getName() != null) ids.add(c.getName());
                });
                courseRepository.findByCode(trimmed).ifPresent(c -> {
                    if (c.getId() != null) ids.add(c.getId());
                    if (c.getName() != null) ids.add(c.getName());
                });
            }
        } catch (Exception ignored) {
        }
        return ids.stream().distinct().toList();
    }

    public List<String> getMatchingDeptIdentifiers(String deptIdentifier) {
        if (deptIdentifier == null || deptIdentifier.isBlank()) return List.of();
        if (metadataCacheService != null) {
            return metadataCacheService.getMatchingDeptIdentifiers(deptIdentifier);
        }
        String trimmed = deptIdentifier.trim();
        List<String> ids = new ArrayList<>();
        ids.add(trimmed);
        try {
            if (departmentRepository != null) {
                departmentRepository.findById(trimmed).ifPresent(d -> {
                    if (d.getCode() != null) ids.add(d.getCode());
                    if (d.getName() != null) ids.add(d.getName());
                });
                departmentRepository.findByCode(trimmed).ifPresent(d -> {
                    if (d.getId() != null) ids.add(d.getId());
                    if (d.getName() != null) ids.add(d.getName());
                });
            }
        } catch (Exception ignored) {
        }
        return ids.stream().distinct().toList();
    }

    public String resolveDepartmentName(String departmentId, Map<String, String> cache) {
        if (departmentId == null || departmentId.isBlank()) return "Computer Applications";
        if (metadataCacheService != null) {
            return metadataCacheService.resolveDepartmentName(departmentId);
        }
        if (cache != null && cache.containsKey(departmentId)) {
            return cache.get(departmentId);
        }
        String resolved = departmentId;
        if (departmentRepository != null) {
            Optional<Department> deptOpt = departmentRepository.findById(departmentId)
                    .or(() -> departmentRepository.findByCode(departmentId))
                    .or(() -> departmentRepository.findByName(departmentId));
            if (deptOpt.isPresent()) {
                resolved = deptOpt.get().getName();
            }
        }
        if (cache != null) {
            cache.put(departmentId, resolved);
        }
        return resolved;
    }

    public String resolveCourseName(String courseId, Map<String, String> cache) {
        if (courseId == null || courseId.isBlank()) return "";
        if (metadataCacheService != null) {
            return metadataCacheService.resolveCourseName(courseId);
        }
        if (cache != null && cache.containsKey(courseId)) {
            return cache.get(courseId);
        }
        String resolved = courseId;
        if (courseRepository != null) {
            Optional<Course> crsOpt = courseRepository.findById(courseId)
                    .or(() -> courseRepository.findByCode(courseId))
                    .or(() -> courseRepository.findByName(courseId));
            if (crsOpt.isPresent()) {
                resolved = crsOpt.get().getName();
            }
        }
        if (cache != null) {
            cache.put(courseId, resolved);
        }
        return resolved;
    }

    public StudentResponse getStudentById(String id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.STAFF) {
            if (currentUser.getCourseId() != null && !currentUser.getCourseId().isBlank()) {
                List<String> allowedCourses = getMatchingCourseIdentifiers(currentUser.getCourseId());
                if (!allowedCourses.contains(student.getCourseId())) {
                    throw new AccessDeniedException("Forbidden: Staff can only access students in their assigned course (" + currentUser.getCourseId() + ")");
                }
            }
            if (currentUser.getDepartmentId() != null && !currentUser.getDepartmentId().isBlank()) {
                List<String> allowedDepts = getMatchingDeptIdentifiers(currentUser.getDepartmentId());
                if (!allowedDepts.contains(student.getDepartmentId())) {
                    throw new AccessDeniedException("Forbidden: Staff cannot access students from other departments");
                }
            }
        }

        SecurityUtils.enforceStudentReadAccess(student.getId(), student.getDepartmentId());
        String deptName = resolveDepartmentName(student.getDepartmentId(), null);
        String crsName = resolveCourseName(student.getCourseId(), null);
        return StudentResponse.fromEntity(student, deptName, crsName);
    }

    @Transactional
    public StudentResponse createStudent(StudentRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.HOD) {
            if (currentUser.getDepartmentId() != null && !currentUser.getDepartmentId().equals(request.getDepartmentId())) {
                throw new AccessDeniedException("Forbidden: HOD can only manage students in their own department");
            }
        }

        SecurityUtils.enforceStudentWriteAccess(request.getDepartmentId());

        String rollNo = request.getRollNo().trim();
        if (studentRepository.existsByRollNo(rollNo)) {
            throw new DuplicateResourceException("Student with roll number " + rollNo + " already exists");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (studentRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Student with email " + email + " already exists");
        }

        String courseId = (request.getCourseId() != null && !request.getCourseId().isBlank())
                ? request.getCourseId().trim()
                : request.getDepartmentId().trim();
        ProgramType programType = request.getProgramType() != null ? request.getProgramType() : ProgramType.UG;
        validateProgramDuration(programType, request.getYear());

        Student student = new Student(
                rollNo,
                request.getName().trim(),
                request.getEmail().trim().toLowerCase(),
                request.getPhone() != null ? request.getPhone().trim() : null,
                request.getDepartmentId().trim(),
                courseId,
                programType,
                request.getYear(),
                request.getSection().trim().toUpperCase(),
                request.getActive() != null ? request.getActive() : true
        );

        Student savedStudent = studentRepository.save(student);

        // Sync with UserRepository so student can login via Google
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            String studentEmail = student.getEmail().trim().toLowerCase();
            userRepository.findByEmail(studentEmail).ifPresentOrElse(
                    existing -> {
                        existing.setName(student.getName());
                        existing.setRole(Role.STUDENT);
                        existing.setDepartmentId(student.getDepartmentId());
                        existing.setActive(student.isActive());
                        userRepository.save(existing);
                    },
                    () -> {
                        String uid = "stu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                        User user = new User(
                                uid,
                                student.getName(),
                                studentEmail,
                                Role.STUDENT,
                                student.getDepartmentId(),
                                student.isActive()
                        );
                        userRepository.save(user);
                    }
            );
        }

        String deptName = resolveDepartmentName(savedStudent.getDepartmentId(), null);
        String crsName = resolveCourseName(savedStudent.getCourseId(), null);
        return StudentResponse.fromEntity(savedStudent, deptName, crsName);
    }

    @Transactional
    public BulkStudentImportResponse createStudentsBulk(List<StudentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new BulkStudentImportResponse(0, 0, 0, List.of(), List.of());
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<StudentResponse> saved = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            StudentRequest req = requests.get(i);
            try {
                if (req.getRollNo() == null || req.getRollNo().isBlank()) {
                    throw new IllegalArgumentException("Roll number is required");
                }
                if (req.getName() == null || req.getName().isBlank()) {
                    throw new IllegalArgumentException("Student name is required");
                }
                if (req.getEmail() == null || req.getEmail().isBlank()) {
                    throw new IllegalArgumentException("Email is required");
                }
                if (req.getDepartmentId() == null || req.getDepartmentId().isBlank()) {
                    throw new IllegalArgumentException("Department is required");
                }
                if (req.getYear() == null) {
                    req.setYear(1);
                }
                if (req.getSection() == null || req.getSection().isBlank()) {
                    req.setSection("A");
                }
                if (req.getProgramType() == null) {
                    req.setProgramType(ProgramType.UG);
                }
                StudentResponse res = createStudent(req);
                saved.add(res);
                imported++;
            } catch (Exception ex) {
                skipped++;
                String identifier = (req != null && req.getRollNo() != null && !req.getRollNo().isBlank())
                        ? req.getRollNo()
                        : "Row " + (i + 1);
                errors.add(identifier + ": " + ex.getMessage());
            }
        }

        return new BulkStudentImportResponse(requests.size(), imported, skipped, errors, saved);
    }

    @Transactional
    public StudentResponse updateStudent(String id, StudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        SecurityUtils.enforceStudentWriteAccess(student.getDepartmentId());
        SecurityUtils.enforceStudentWriteAccess(request.getDepartmentId());

        String newRollNo = request.getRollNo().trim();
        if (!student.getRollNo().equalsIgnoreCase(newRollNo) && studentRepository.existsByRollNo(newRollNo)) {
            throw new DuplicateResourceException("Student with roll number " + newRollNo + " already exists");
        }

        String newEmail = request.getEmail().trim().toLowerCase();
        if (!student.getEmail().equalsIgnoreCase(newEmail) && studentRepository.existsByEmail(newEmail)) {
            throw new DuplicateResourceException("Student with email " + newEmail + " already exists");
        }

        student.setRollNo(newRollNo);
        student.setName(request.getName().trim());
        student.setEmail(request.getEmail().trim().toLowerCase());
        student.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        student.setDepartmentId(request.getDepartmentId().trim());
        if (request.getCourseId() != null && !request.getCourseId().isBlank()) {
            student.setCourseId(request.getCourseId().trim());
        } else if (student.getCourseId() == null || student.getCourseId().isBlank()) {
            student.setCourseId(request.getDepartmentId().trim());
        }
        ProgramType resolvedProgramType = request.getProgramType() != null ? request.getProgramType() : student.getProgramType();
        validateProgramDuration(resolvedProgramType, request.getYear());
        if (request.getProgramType() != null) {
            student.setProgramType(request.getProgramType());
        }
        student.setYear(request.getYear());
        student.setSection(request.getSection().trim().toUpperCase());
        if (request.getActive() != null) {
            student.setActive(request.getActive());
        }

        Student updatedStudent = studentRepository.save(student);

        // Sync with UserRepository
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            String studentEmail = student.getEmail().trim().toLowerCase();
            userRepository.findByEmail(studentEmail).ifPresentOrElse(
                    u -> {
                        u.setName(student.getName());
                        u.setActive(student.isActive());
                        u.setDepartmentId(student.getDepartmentId());
                        userRepository.save(u);
                    },
                    () -> {
                        String uid = "stu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                        User user = new User(
                                uid,
                                student.getName(),
                                studentEmail,
                                Role.STUDENT,
                                student.getDepartmentId(),
                                student.isActive()
                        );
                        userRepository.save(user);
                    }
            );
        }

        String deptName = resolveDepartmentName(updatedStudent.getDepartmentId(), null);
        String crsName = resolveCourseName(updatedStudent.getCourseId(), null);
        return StudentResponse.fromEntity(updatedStudent, deptName, crsName);
    }

    @Transactional
    public void deleteStudent(String id) {
        Student student = studentRepository.findById(id)
                .or(() -> studentRepository.findByRollNo(id))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));
        SecurityUtils.enforceStudentWriteAccess(student.getDepartmentId());

        // 1. Delete associated User account
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            userRepository.findByEmail(student.getEmail().trim().toLowerCase())
                    .ifPresent(userRepository::delete);
        }

        // 2. Cascade delete all attendance records for this student
        if (attendanceRepository != null) {
            if (student.getId() != null) {
                attendanceRepository.deleteByStudentId(student.getId());
            }
            if (student.getRollNo() != null && !student.getRollNo().isBlank()) {
                attendanceRepository.deleteByStudentId(student.getRollNo().trim());
            }
        }

        // 3. Cascade delete all archived attendance records for this student
        if (archiveRepository != null) {
            if (student.getId() != null) {
                archiveRepository.deleteByStudentId(student.getId());
            }
            if (student.getRollNo() != null && !student.getRollNo().isBlank()) {
                archiveRepository.deleteByStudentId(student.getRollNo().trim());
            }
        }

        // 4. Delete the student entity
        studentRepository.delete(student);
    }

    @Transactional
    public BulkDeleteResponse deleteStudentsBulk(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new BulkDeleteResponse(0, 0, 0, List.of());
        }
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            try {
                deleteStudent(id.trim());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add("Failed to delete " + id + ": " + e.getMessage());
            }
        }
        return new BulkDeleteResponse(ids.size(), successCount, failureCount, errors);
    }

    private void validateProgramDuration(ProgramType programType, Integer year) {
        if (programType == null || year == null) return;
        if (programType == ProgramType.PG && (year < 1 || year > 2)) {
            throw new IllegalArgumentException("PG program duration is 2 years (Year 1 or 2). Invalid year: " + year);
        } else if (programType == ProgramType.UG && (year < 1 || year > 3)) {
            throw new IllegalArgumentException("UG program duration is 3 years (Year 1, 2, or 3). Invalid year: " + year);
        }
    }
}
