package com.kasztelanic.carcare.service.dto;

import lombok.Value;

/**
 * A DTO representing a password change required data - current and new password.
 */
@Value(staticConstructor = "of")
public class PasswordChangeDto {

    String currentPassword;
    String newPassword;
}
