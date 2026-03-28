package org.chibios.chibiforge.config;

/**
 * Resolves and validates the active target for generation.
 * Phase 1: only "default" target is supported.
 */
public class TargetResolver {

    public String resolve(ChibiForgeConfiguration config, String requestedTarget) {
        if (requestedTarget == null || requestedTarget.isBlank()) {
            return "default";
        }
        if (!config.getTargets().contains(requestedTarget)) {
            throw new IllegalArgumentException(
                    "Target '" + requestedTarget + "' not defined in configuration. " +
                    "Available targets: " + config.getTargets());
        }
        return requestedTarget;
    }
}
