package dev.modulithforge.admin.dto;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.identity.model.UserType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Pattern(regexp = "^[+\\d\\s\\-()]*$", message = "Phone number contains invalid characters")
    private String phone;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;
    private UserType userType;
    private Boolean emailVerified;
}
