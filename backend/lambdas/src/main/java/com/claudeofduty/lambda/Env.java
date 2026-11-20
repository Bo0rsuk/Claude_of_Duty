package com.claudeofduty.lambda;

/** Reads the configuration that Terraform passes to the functions as environment variables. */
public final class Env {

    private Env() {
    }

    public static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }

    public static String get(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
