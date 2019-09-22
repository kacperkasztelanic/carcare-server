package com.kasztelanic.carcare.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A DTO representing a password change required data - current and new password.
 */
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PasswordChangeDto {

    @Getter
    @Setter
    private String currentPassword;

    @Getter
    @Setter
    private String newPassword;
}
