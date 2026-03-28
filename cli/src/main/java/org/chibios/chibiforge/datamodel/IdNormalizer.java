package org.chibios.chibiforge.datamodel;

/**
 * Normalizes identifiers per the ChibiForge specification (§2.1).
 *
 * Rules:
 * 1. Convert to lowercase.
 * 2. Replace any character that is not a letter, digit, or underscore with '_'.
 * 3. Collapse multiple consecutive underscores into a single '_'.
 */
public final class IdNormalizer {

    private IdNormalizer() {}

    public static String normalize(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        String lower = id.toLowerCase();
        String replaced = lower.replaceAll("[^a-z0-9_]", "_");
        String collapsed = replaced.replaceAll("_+", "_");
        return collapsed;
    }
}
