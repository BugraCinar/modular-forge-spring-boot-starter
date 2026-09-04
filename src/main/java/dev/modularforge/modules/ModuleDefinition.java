package dev.modularforge.modules;

import java.util.List;

public record ModuleDefinition(
        String id,
        String displayName,
        String description,
        String packageName,
        ModuleKind kind,
        boolean enabled,
        String toggle,
        List<String> dependsOn,
        String removalGuide) {

    public enum ModuleKind {
        REQUIRED,
        OPTIONAL,
        REPLACEABLE
    }
}
