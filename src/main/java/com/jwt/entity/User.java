package com.jwt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long userId;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private UserStatus status;

    @PrePersist
    void prePersist() {
        if (role == null) {
            role = UserRole.USER;
        }
        if (status == null) {
            status = UserStatus.PENDING;
        }
    }

    public String getRole() {
        return role != null ? role.name() : null;
    }

    public void setRole(String role) {
        if (role == null) {
            this.role = null;
            return;
        }
        this.role = "OWNER".equals(role) ? UserRole.ADMIN : UserRole.valueOf(role);
    }

    public UserRole getRoleEnum() {
        return role;
    }

    public void setRoleEnum(UserRole role) {
        this.role = role;
    }

    public String getStatus() {
        return status != null ? status.name() : null;
    }

    public void setStatus(String status) {
        this.status = status == null ? null : UserStatus.valueOf(status);
    }

    public UserStatus getStatusEnum() {
        return status != null ? status : UserStatus.PENDING;
    }

    public void setStatusEnum(UserStatus status) {
        this.status = status;
    }
}
