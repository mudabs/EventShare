package com.eventshare.api.user;

import com.eventshare.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "clerk_user_id", nullable = false)
    private String clerkUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.HOST;

    /** Admin-controlled account suspension; disabled users are rejected at the boundary. */
    @Column(name = "disabled", nullable = false)
    private boolean disabled = false;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
