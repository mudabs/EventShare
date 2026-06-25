package com.eventshare.api.common.security;

import com.eventshare.api.common.error.ForbiddenException;
import com.eventshare.api.user.Role;
import com.eventshare.api.user.User;
import org.springframework.stereotype.Component;

/** Authorizes platform-admin actions based on the persisted user role. */
@Component
public class AdminGuard {
    public void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Administrator access required");
        }
    }
}
