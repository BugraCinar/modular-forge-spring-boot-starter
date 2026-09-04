package dev.modulithforge.admin.dto;

import dev.modulithforge.identity.model.Admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {
    private List<AdminUserDTO> users;
    private int currentPage;
    private int totalPages;
    private long totalItems;
    private int pageSize;
}
