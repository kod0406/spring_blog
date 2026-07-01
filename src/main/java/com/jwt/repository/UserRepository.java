package com.jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    boolean existsByRole(UserRole role);
    long countByRole(UserRole role);
    List<User> findByStatusOrderByUserIdAsc(UserStatus status);
    List<User> findAllByOrderByUserIdAsc();
}
