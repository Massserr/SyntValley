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

    public static final ModConfigSpec.BooleanValue AI_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> AI_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> AI_MODEL;
    public static final ModConfigSpec.LongValue AI_CONNECT_TIMEOUT_MILLIS;
    public static final ModConfigSpec.LongValue AI_REQUEST_TIMEOUT_MILLIS;
    public static final ModConfigSpec.IntValue AI_QUEUE_CAPACITY;

    static {
        var builder = new ModConfigSpec.Builder();
        STARTUP_DIAGNOSTICS = builder
                .comment("Log the Java and platform versions during common setup.")
                .translation(STARTUP_DIAGNOSTICS_TRANSLATION_KEY)
                .define("startupDiagnostics", true);

        builder.comment("Optional local LLM advisor (Ollama). Gameplay never depends on it.").push("ai");
        AI_ENABLED = builder
                .comment("Enable the Ollama backend. With false (or Ollama absent) everything runs deterministically.")
                .define("enabled", false);
        AI_BASE_URL = builder
                .comment("Ollama base URL.")
                .define("baseUrl", "http://127.0.0.1:11434");
        AI_MODEL = builder
                .comment("Model name to request.")
                .define("model", "qwen3:8b");
        AI_CONNECT_TIMEOUT_MILLIS = builder
                .comment("TCP connect deadline, milliseconds.")
                .defineInRange("connectTimeoutMillis", 2_000L, 100L, 60_000L);
        AI_REQUEST_TIMEOUT_MILLIS = builder
                .comment("Whole-request deadline, milliseconds.")
                .defineInRange("requestTimeoutMillis", 30_000L, 500L, 300_000L);
        AI_QUEUE_CAPACITY = builder
                .comment("Maximum queued LLM jobs; further submits are rejected immediately.")
                .defineInRange("queueCapacity", 4, 1, 64);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private SyntValleyConfig() {
    }
}
