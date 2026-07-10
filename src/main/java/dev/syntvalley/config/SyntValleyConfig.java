package dev.syntvalley.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Bootstrap-only common configuration. Gameplay systems add their settings in their own slices.
 */
public final class SyntValleyConfig {
    public static final String STARTUP_DIAGNOSTICS_TRANSLATION_KEY =
            "syntvalley.configuration.startup_diagnostics";

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue STARTUP_DIAGNOSTICS;

    static {
        var builder = new ModConfigSpec.Builder();
        STARTUP_DIAGNOSTICS = builder
                .comment("Log the Java and platform versions during common setup.")
                .translation(STARTUP_DIAGNOSTICS_TRANSLATION_KEY)
                .define("startupDiagnostics", true);
        COMMON_SPEC = builder.build();
    }

    private SyntValleyConfig() {
    }
}
