package com.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ams.model.Role;
import com.ams.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmail(String email);

    boolean existsByFirebaseUid(String firebaseUid);

    boolean existsByEmail(String email);

    List<User> findByDepartmentIdAndActiveTrue(String departmentId);

    List<User> findByRoleAndActiveTrue(Role role);

    long countByRoleInAndActiveTrue(java.util.Collection<Role> roles);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.departmentId IN :departmentIds AND u.role IN :roles AND u.active = true")
    long countByDepartmentIdInAndRoleInAndActiveTrue(
            @org.springframework.data.repository.query.Param("departmentIds") java.util.Collection<String> departmentIds,
            @org.springframework.data.repository.query.Param("roles") java.util.Collection<Role> roles
    );
}
