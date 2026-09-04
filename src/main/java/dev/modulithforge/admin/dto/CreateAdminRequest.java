package dev.modulithforge.admin.dto;

import dev.modulithforge.identity.model.Admin;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    private String password;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePicture;

    @NotNull(message = "Admin level is required")
    @Min(value = 1, message = "Admin level must be 1 (Admin) or 2 (Moderator)")
    @Max(value = 2, message = "Admin level must be 1 (Admin) or 2 (Moderator)")
    private Integer level;

    private List<String> permissions;

    private Boolean isActive = true;
}
