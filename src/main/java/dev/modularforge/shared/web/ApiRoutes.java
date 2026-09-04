package dev.modularforge.shared.web;

import dev.modularforge.identity.model.Admin;
public final class ApiRoutes {

    public static final String ADMIN_BASE = "/api/v1/admin";
    public static final String DATABASE_BACKUP = ADMIN_BASE + "/database-backup";
    public static final String ADMIN_IMAGE = ADMIN_BASE + "/image";

    private ApiRoutes() {
    }
}
