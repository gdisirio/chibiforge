package org.chibios.chibiforge.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects actions taken during generation. Supports dry-run reporting.
 */
public class GenerationReport {

    private final List<GenerationAction> actions = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addAction(GenerationAction action) {
        actions.add(action);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<GenerationAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public long countByType(GenerationAction.Type type) {
        return actions.stream().filter(a -> a.getType() == type).count();
    }

    public void printSummary() {
        for (String warning : warnings) {
            System.out.println("WARNING: " + warning);
        }
        for (GenerationAction action : actions) {
            System.out.println("  " + action);
        }
        System.out.println("Generation complete: " +
                countByType(GenerationAction.Type.COPY) + " copied, " +
                countByType(GenerationAction.Type.SKIP) + " skipped, " +
                countByType(GenerationAction.Type.TEMPLATE) + " templates processed.");
    }
}
