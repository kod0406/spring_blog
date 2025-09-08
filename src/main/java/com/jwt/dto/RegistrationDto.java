package com.jwt.dto;

import lombok.Data;

@Data
public class RegistrationDto {
    private String name;
    private String email;
    private String password;
}
