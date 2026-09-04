package dev.modularforge.auth.dto;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Username or email is required")
    @Size(max = 255, message = "Username or email must not exceed 255 characters")
    private String username; // Can be username or email

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password must not exceed 128 characters")
    private String password;

    @Pattern(regexp = "^(user|admin)?$", message = "Role must be 'user' or 'admin'")
    private String role; // "user" or "admin" - specifies which account type to login as

    private String captchaToken; // Google reCAPTCHA token (required for admin login when enabled)
}