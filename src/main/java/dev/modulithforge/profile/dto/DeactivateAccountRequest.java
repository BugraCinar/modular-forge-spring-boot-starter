package dev.modulithforge.profile.dto;

import dev.modulithforge.identity.model.User;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateAccountRequest {

    @NotBlank(message = "Password confirmation is required to deactivate your account")
    private String password;
}
