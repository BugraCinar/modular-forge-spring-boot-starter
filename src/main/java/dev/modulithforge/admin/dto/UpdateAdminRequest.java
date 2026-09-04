package dev.modulithforge.admin.dto;

import dev.modulithforge.identity.model.Admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminRequest {

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePicture;

    @Min(value = 1, message = "Admin level must be 1 (Admin) or 2 (Moderator)")
    @Max(value = 2, message = "Admin level must be 1 (Admin) or 2 (Moderator)")
    private Integer level;

    private List<String> permissions;

    private Boolean isActive;
}
