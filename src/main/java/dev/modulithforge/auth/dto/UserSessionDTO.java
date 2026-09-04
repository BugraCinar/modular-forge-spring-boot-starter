package dev.modulithforge.auth.dto;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.identity.model.UserType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String role; // "USER" or "ADMIN"
    private String userType;
    private Integer adminLevel; // Only for admins
}
