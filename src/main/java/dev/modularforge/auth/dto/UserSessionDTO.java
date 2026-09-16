package dev.modularforge.auth.dto;


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
