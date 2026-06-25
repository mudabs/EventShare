package com.eventshare.api.whitelist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddWhitelistRequest(
        @NotBlank @Email String email,
        @Size(max = 500) String note
) {
}
