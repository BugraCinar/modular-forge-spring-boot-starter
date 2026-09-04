package dev.modulithforge.auth.dto;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPasswordRequest {
    @NotBlank(message = "Username or email is required")
    @Size(max = 255, message = "Username or email must not exceed 255 characters")
    private String username; // Can be username or email

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password must not exceed 128 characters")
    private String password;

    @Pattern(regexp = "^(user|admin)?$", message = "Role must be 'user' or 'admin'")
    private String role; // Optional: "user" or "admin"
}