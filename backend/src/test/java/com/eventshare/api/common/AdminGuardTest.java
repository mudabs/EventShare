package com.eventshare.api.common;

import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.common.security.AdminGuard;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminGuardTest {

    private final AdminGuard guard = new AdminGuard();

    @Test
    void allowsAdmin() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        assertThatCode(() -> guard.requireAdmin(admin)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonAdmin() {
        User host = new User();
        host.setRole(Role.HOST);
        assertThatThrownBy(() -> guard.requireAdmin(host)).isInstanceOf(ForbiddenException.class);
    }
}
