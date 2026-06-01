package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Test
    void adminAccountIsBootstrappedFromAdminSettings() {
        User admin = userRepository.findByEmail("admin@example.com");

        assertThat(admin).isNotNull();
        assertThat(admin.getRoleEnum()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatusEnum()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void legacyOwnerRoleInputIsTreatedAsAdmin() {
        User user = new User();
        user.setRole("OWNER");

        assertThat(user.getRoleEnum()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void registeredUserStartsPendingAndCannotLoginUntilApproved() {
        RegistrationDto request = new RegistrationDto();
        request.setName("Member");
        request.setEmail("member-login@example.com");
        request.setPassword("password");

        User user = userService.registerUser(request);

        assertThat(user.getStatusEnum()).isEqualTo(UserStatus.PENDING);
        assertThatThrownBy(() -> userService.authenticateUser("member-login@example.com", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 승인 대기 중");

        userService.approveUser(user.getUserId());
        User authenticated = userService.authenticateUser("member-login@example.com", "password");

        assertThat(authenticated.getStatusEnum()).isEqualTo(UserStatus.ACTIVE);
    }
}
