package com.eventshare.api.demo;

import com.eventshare.api.common.error.NotFoundException;
import com.eventshare.api.common.security.AdminGuard;
import com.eventshare.api.common.security.CurrentUser;
import com.eventshare.api.config.AppProperties;
import com.eventshare.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo endpoints.
 * <ul>
 *   <li>{@code GET /api/demo/info} (public): whether demo mode is on, the guest link,
 *       and, only when {@code DEMO_SHOW_CREDENTIALS=true}, the demo logins. The landing
 *       page uses it to show a "Try the demo" panel.</li>
 *   <li>{@code POST /api/admin/demo/reset} (platform admin): reset on demand, for
 *       example right before an interview.</li>
 * </ul>
 */
@Tag(name = "Demo")
@RestController
public class DemoController {

    private final DemoProperties props;
    private final DemoSeeder seeder;
    private final AdminGuard adminGuard;
    private final String appBaseUrl;

    public DemoController(DemoProperties props, DemoSeeder seeder, AdminGuard adminGuard, AppProperties app) {
        this.props = props;
        this.seeder = seeder;
        this.adminGuard = adminGuard;
        this.appBaseUrl = app.appBaseUrl() == null ? "" : app.appBaseUrl().replaceAll("/+$", "");
    }

    @Operation(summary = "Demo mode status and guest link (public)")
    @GetMapping("/api/demo/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.enabled());
        if (!props.enabled()) {
            return body;
        }
        body.putAll(seeder.publicInfo());
        body.put("guestUrl", appBaseUrl + "/e/" + props.inviteCode());
        if (props.showCredentials()) {
            List<Map<String, String>> logins = new ArrayList<>();
            logins.add(Map.of("role", "Host", "username", props.hostUsername(),
                    "password", nullToEmpty(props.hostPassword())));
            if (props.adminEnabled()) {
                logins.add(Map.of("role", "Admin", "username", props.adminUsername(),
                        "password", nullToEmpty(props.adminPassword())));
            }
            body.put("logins", logins);
        }
        return body;
    }

    @Operation(summary = "Demo settings including the promo code (admin)")
    @GetMapping("/api/admin/demo/info")
    public Map<String, Object> adminInfo(@CurrentUser User admin) {
        adminGuard.requireAdmin(admin);
        Map<String, Object> body = info();
        if (props.enabled()) {
            // Kept off the public endpoint so it is not advertised on the landing page.
            body.put("promoCode", props.promoCode());
        }
        return body;
    }

    @Operation(summary = "Reset the demo data now (admin)")
    @PostMapping("/api/admin/demo/reset")
    public DemoSeeder.DemoResetResult reset(@CurrentUser User admin) {
        adminGuard.requireAdmin(admin);
        if (!props.enabled()) {
            throw new NotFoundException("Demo mode is not enabled");
        }
        return seeder.reset("admin:" + admin.getEmail());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
