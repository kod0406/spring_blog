package com.jwt.service;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean isAdmin(User user) {
        return user != null && (user.getRoleEnum() == UserRole.OWNER || user.getRoleEnum() == UserRole.ADMIN);
    }

    public boolean isActiveUser(User user) {
        return user != null && user.getStatusEnum() == UserStatus.ACTIVE;
    }

    public void requireLogin(User user) {
        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
    }

    public void requireActiveUser(User user) {
        requireLogin(user);
        if (user.getStatusEnum() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("관리자 승인 후 이용할 수 있습니다.");
        }
    }

    public void requireAdmin(User user) {
        requireActiveUser(user);
        if (!isAdmin(user)) {
            throw new IllegalArgumentException("관리자 권한이 필요합니다.");
        }
    }
}
