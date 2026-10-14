package com.ams.service;

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

import com.ams.dto.PageResponse;
import com.ams.dto.UserRequest;
import com.ams.dto.UserResponse;
import com.ams.exception.AccessDeniedException;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Course;
import com.ams.model.Department;
import com.ams.model.Role;
import com.ams.model.User;
import com.ams.repository.CourseRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;
import com.ams.security.AuthenticatedUser;
import com.ams.security.SecurityUtils;
import com.ams.specification.UserSpecification;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;

    @Autowired
    public UserService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
    }

    public UserService(
            UserRepository userRepository,
            StudentRepository studentRepository) {
        this(userRepository, studentRepository, null, null);
    }

    public PageResponse<UserResponse> getUsers(
            Role role,
            String departmentId,
            Boolean active,
            int page,
            int size) {

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            Role userRole = currentUser.getRole();
            if (userRole == Role.STAFF || userRole == Role.STUDENT) {
                throw new AccessDeniedException("Forbidden: " + userRole + " is not permitted to view user directory");
            }
            if (userRole == Role.HOD && currentUser.getDepartmentId() != null) {
                departmentId = currentUser.getDepartmentId();
            }
        }

        Specification<User> spec = UserSpecification.filter(role, departmentId, active);

        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0) ? 20 : Math.min(100, size);

        PageRequest pageRequest = PageRequest.of(validatedPage, validatedSize, Sort.by("name").ascending());
        Page<User> pagedResult = userRepository.findAll(spec, pageRequest);

        Map<String, String> deptNames = new HashMap<>();
        Map<String, String> crsNames = new HashMap<>();

        List<UserResponse> data = pagedResult.getContent().stream()
                .map(u -> UserResponse.fromEntity(
                        u,
                        resolveDepartmentName(u.getDepartmentId(), deptNames),
                        resolveCourseName(u.getCourseId(), crsNames)
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

    public String resolveDepartmentName(String departmentId, Map<String, String> cache) {
        if (departmentId == null || departmentId.isBlank()) return "Administration";
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

    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            Role userRole = currentUser.getRole();
            if (userRole == Role.STAFF || userRole == Role.STUDENT) {
                if (!currentUser.getUserId().equals(user.getId())) {
                    throw new AccessDeniedException("Forbidden: Users can only view their own profile");
                }
            } else if (userRole == Role.HOD) {
                if (currentUser.getDepartmentId() != null && !currentUser.getDepartmentId().equals(user.getDepartmentId())) {
                    throw new AccessDeniedException("Forbidden: HOD can only view users in their own department");
                }
            }
        }

        String deptName = resolveDepartmentName(user.getDepartmentId(), null);
        String crsName = resolveCourseName(user.getCourseId(), null);
        return UserResponse.fromEntity(user, deptName, crsName);
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        SecurityUtils.enforceStaffManagementAccess();

        String email = request.getEmail().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            if (!existing.isActive()) {
                if (request.getRole() == Role.VP) {
                    boolean vpExists = userRepository.findAll().stream()
                            .anyMatch(u -> u.getRole() == Role.VP && u.isActive() && !u.getId().equals(existing.getId()));
                    if (vpExists) {
                        throw new DuplicateResourceException("Only 1 active Vice Principal (VP) is permitted in the institution.");
                    }
                }
                existing.setName(request.getName().trim());
                existing.setRole(request.getRole());
                existing.setDepartmentId(request.getDepartmentId() != null ? request.getDepartmentId().trim() : null);
                existing.setCourseId(request.getCourseId() != null ? request.getCourseId().trim() : null);
                existing.setActive(true);
                User reactivated = userRepository.save(existing);
                String dName = resolveDepartmentName(reactivated.getDepartmentId(), null);
                String cName = resolveCourseName(reactivated.getCourseId(), null);
                return UserResponse.fromEntity(reactivated, dName, cName);
            }
            throw new DuplicateResourceException("User with email " + email + " already exists");
        }

        String firebaseUid = request.getFirebaseUid();
        if (firebaseUid == null || firebaseUid.isBlank()) {
            firebaseUid = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } else {
            firebaseUid = firebaseUid.trim();
            if (userRepository.existsByFirebaseUid(firebaseUid)) {
                throw new DuplicateResourceException("User with Firebase UID " + firebaseUid + " already exists");
            }
        }

        if (request.getRole() == Role.VP) {
            boolean vpExists = userRepository.findAll().stream()
                    .anyMatch(u -> u.getRole() == Role.VP && u.isActive());
            if (vpExists) {
                throw new DuplicateResourceException("Only 1 active Vice Principal (VP) is permitted in the institution.");
            }
        }

        User user = new User(
                firebaseUid,
                request.getName().trim(),
                email,
                request.getRole(),
                request.getDepartmentId() != null ? request.getDepartmentId().trim() : null,
                request.getCourseId() != null ? request.getCourseId().trim() : null,
                request.getPhone() != null ? request.getPhone().trim() : null,
                request.getActive() != null ? request.getActive() : true
        );

        User saved = userRepository.save(user);
        String dName = resolveDepartmentName(saved.getDepartmentId(), null);
        String cName = resolveCourseName(saved.getCourseId(), null);
        return UserResponse.fromEntity(saved, dName, cName);
    }

    @Transactional
    public UserResponse updateUser(String id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            boolean isSelf = (currentUser.getUserId() != null && currentUser.getUserId().equals(id))
                    || (currentUser.getEmail() != null && user.getEmail() != null && currentUser.getEmail().equalsIgnoreCase(user.getEmail()));
            boolean isAdminOrVp = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.VP;
            if (!isSelf && !isAdminOrVp) {
                throw new AccessDeniedException("Forbidden: Only ADMIN, VP, or the account owner can update account details");
            }
            if (!isAdminOrVp) {
                // Non-admin self-update: preserve role and active status
                request.setRole(user.getRole());
                request.setActive(user.isActive());
            }
        }

        if (request.getRole() == Role.VP) {
            boolean isActivating = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
            if (isActivating) {
                boolean otherVpExists = userRepository.findAll().stream()
                        .anyMatch(u -> u.getRole() == Role.VP && u.isActive() && !u.getId().equals(id));
                if (otherVpExists) {
                    throw new DuplicateResourceException("Only 1 active Vice Principal (VP) is permitted in the institution.");
                }
            }
        }

        String newEmail = request.getEmail().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new DuplicateResourceException("User with email " + newEmail + " already exists");
        }

        user.setName(request.getName().trim());
        user.setEmail(newEmail);
        user.setRole(request.getRole());
        user.setDepartmentId(request.getDepartmentId() != null ? request.getDepartmentId().trim() : null);
        user.setCourseId(request.getCourseId() != null ? request.getCourseId().trim() : null);
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        User updated = userRepository.save(user);
        String dName = resolveDepartmentName(updated.getDepartmentId(), null);
        String cName = resolveCourseName(updated.getCourseId(), null);
        return UserResponse.fromEntity(updated, dName, cName);
    }

    public Optional<User> findUserEntityById(String id) {
        return userRepository.findById(id);
    }

    @Transactional
    public void deleteUser(String id) {
        SecurityUtils.enforceStaffManagementAccess();

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String normEmail = user.getEmail().trim().toLowerCase();
            studentRepository.findByEmail(normEmail).ifPresent(studentRepository::delete);
        }

        userRepository.delete(user);
    }
}
