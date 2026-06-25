package com.eventshare.api.whitelist;

import com.eventshare.api.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** An email granted unlimited/premium access with no billing. */
@Getter
@Setter
@Entity
@Table(name = "whitelisted_users")
public class WhitelistedUser extends BaseEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "note")
    private String note;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
