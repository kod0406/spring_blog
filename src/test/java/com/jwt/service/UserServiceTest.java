package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbcTemplate;

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
void legacyOwnerRoleRowsAreNormalizedOnStartup() {
    // 💡 변경: jdbcTemplate 대신 UserRepository 사용 (JPA가 ID나 필수 컬럼을 알아서 처리함)
    User legacyUser = new User();
    legacyUser.setName("Legacy Owner");
    legacyUser.setEmail("legacy-owner@example.com");
    legacyUser.setPassword("{noop}password");
    legacyUser.setRole("OWNER");
    // status 등 기타 필수 속성 세팅 필요
    userRepository.save(legacyUser);

    userService.run(null);

    User user = userRepository.findByEmail("legacy-owner@example.com");
    assertThat(user.getRoleEnum()).isEqualTo(UserRole.ADMIN);
}

    @Test
    void registeredUserStartsPendingAndCannotLoginUntilApproved() {
        User admin = userRepository.save(User.builder()
                .email("approval-admin@example.com")
                .name("Admin")
                .password("{noop}password")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        RegistrationDto request = new RegistrationDto();
        request.setName("Member");
        request.setEmail("member-login@example.com");
        request.setPassword("password");

        User user = userService.registerUser(request);

        assertThat(user.getStatusEnum()).isEqualTo(UserStatus.PENDING);
        assertThatThrownBy(() -> userService.authenticateUser("member-login@example.com", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 승인 대기 중");

        userService.approveUser(user.getUserId(), admin);
        User authenticated = userService.authenticateUser("member-login@example.com", "password");

        assertThat(authenticated.getStatusEnum()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void approvalOperationsRequireAdminAtServiceLayer() {
        User member = userRepository.save(User.builder()
                .email("approval-member@example.com")
                .name("Member")
                .password("{noop}password")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        User pending = userRepository.save(User.builder()
                .email("approval-pending@example.com")
                .name("Pending")
                .password("{noop}password")
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build());

        assertThatThrownBy(() -> userService.getPendingUsers(member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");
        assertThatThrownBy(() -> userService.approveUser(pending.getUserId(), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");
        assertThatThrownBy(() -> userService.rejectUser(pending.getUserId(), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");
    }
}
