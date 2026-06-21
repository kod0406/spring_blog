package com.jwt.service;

import com.jwt.dto.UserAdminDto;
import com.jwt.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserAdminDtoMapper {
    public UserAdminDto.Response toResponse(User user) {
        return new UserAdminDto.Response(user);
    }
}
