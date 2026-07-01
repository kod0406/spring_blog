package com.jwt.dto;

import com.jwt.entity.User;
import lombok.Getter;

public class UserAdminDto {

    @Getter
    public static class Response {
        private final Long userId;
        private final String name;
        private final String email;
        private final String role;
        private final String status;

        public Response(User user) {
            this.userId = user.getUserId();
            this.name = user.getName();
            this.email = user.getEmail();
            this.role = user.getRole();
            this.status = user.getStatus();
        }
    }
}
