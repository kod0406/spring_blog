package com.jwt.service;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.exception.ForbiddenException;
import com.jwt.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean isAdmin(User user) {
        return user != null && user.getRoleEnum() == UserRole.ADMIN && user.getStatusEnum() == UserStatus.ACTIVE;
    }

    public boolean isActiveUser(User user) {
        return user != null && user.getStatusEnum() == UserStatus.ACTIVE;
    }

    public void requireLogin(User user) {
        if (user == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
    }

    public void requireActiveUser(User user) {
        requireLogin(user);
        if (user.getStatusEnum() != UserStatus.ACTIVE) {
            throw new ForbiddenException("관리자 승인 후 이용할 수 있습니다.");
        }
    }

    public void requireAdmin(User user) {
        requireActiveUser(user);
        if (user.getRoleEnum() != UserRole.ADMIN) {
            throw new ForbiddenException("관리자 권한이 필요합니다.");
        }
    }
}
