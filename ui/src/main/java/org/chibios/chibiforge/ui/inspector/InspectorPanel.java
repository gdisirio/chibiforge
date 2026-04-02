/*
    ChibiOS - Copyright (C) 2025-2026 Giovanni Di Sirio.

    This file is part of ChibiOS.

    ChibiOS is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation version 3 of the License.

    ChibiOS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chibios.chibiforge.ui.inspector;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chibios.chibiforge.component.LayoutDef;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.component.SectionDef;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.ui.model.AppModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Right-side inspector panel with four tabs: Outline, Help, Files, Log.
 */
public class InspectorPanel {

    private final AppModel model;
    private final VBox root;
    private final TabPane tabPane;

    // Outline tab
    private final TreeView<String> outlineTree;
    private Consumer<String> onOutlineSelect;
    private final Map<TreeItem<String>, String> outlineTargets = new HashMap<>();

    // Help tab
    private final VBox helpContent;

    // Files tab
    private final TreeView<String> filesTree;

    // Log tab
    private final TextArea logArea;

    public InspectorPanel(AppModel model) {
        this.model = model;

        // Outline tab
        outlineTree = new TreeView<>();
        outlineTree.setShowRoot(false);
        outlineTree.setRoot(new TreeItem<>("Root"));
        outlineTree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && onOutlineSelect != null) {
                String target = outlineTargets.get(selected);
                if (target != null) {
                    onOutlineSelect.accept(target);
                }
            }
        });
        Tab outlineTab = new Tab("Outline", outlineTree);

        // Help tab
        helpContent = new VBox(8);
        helpContent.setPadding(new Insets(8));
        ScrollPane helpScroll = new ScrollPane(helpContent);
        helpScroll.setFitToWidth(true);
        helpScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Tab helpTab = new Tab("Help", helpScroll);

        // Files tab
        filesTree = new TreeView<>();
        filesTree.setShowRoot(true);
        Tab filesTab = new Tab("Files", filesTree);

        // Log tab
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        logArea.setWrapText(true);
        Tab logTab = new Tab("Log", logArea);

        tabPane = new TabPane(outlineTab, helpTab, filesTab, logTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root = new VBox();
        root.setPrefWidth(300);
        root.setMinWidth(200);
        root.getStyleClass().add("inspector-panel");
        root.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
    }

    /**
     * Set callback when an outline item is clicked. Parameter is the section/property name.
     */
    public void setOnOutlineSelect(Consumer<String> handler) {
        this.onOutlineSelect = handler;
    }

    // --- Outline Tab ---

    /**
     * Show the outline for the top-level components view.
     */
    public void showComponentsOutline() {
        TreeItem<String> treeRoot = new TreeItem<>("Components");
        treeRoot.setExpanded(true);
        outlineTargets.clear();

        if (model.getConfiguration() != null && model.getRegistry() != null) {
            for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
                String name = entry.getComponentId();
                try {
                    ComponentContainer container = model.getRegistry().lookup(entry.getComponentId());
                    name = container.loadDefinition().getName();
                } catch (Exception ignored) {}
                TreeItem<String> item = new TreeItem<>(name);
                treeRoot.getChildren().add(item);
                outlineTargets.put(item, "component:" + entry.getComponentId());
            }
        }

        outlineTree.setRoot(treeRoot);
    }

    /**
     * Show the outline for a specific component's schema structure.
     */
    public void showComponentOutline(ComponentDefinition def) {
        TreeItem<String> treeRoot = new TreeItem<>(def.getName());
        treeRoot.setExpanded(true);
        outlineTargets.clear();

        for (SectionDef section : def.getSections()) {
            TreeItem<String> sectionItem = new TreeItem<>(section.getName());
            sectionItem.setExpanded(true);
            outlineTargets.put(sectionItem, "section:" + section.getName());

            for (Object child : section.getChildren()) {
                if (child instanceof PropertyDef prop) {
                    String label = prop.getName();
                    if (prop.getType() == PropertyDef.Type.LIST) {
                        label += " [list]";
                    }
                    TreeItem<String> propertyItem = new TreeItem<>(label);
                    sectionItem.getChildren().add(propertyItem);
                    outlineTargets.put(propertyItem, "property:" + prop.getName());
                } else if (child instanceof LayoutDef layout) {
                    TreeItem<String> layoutItem = new TreeItem<>("(layout " + layout.getColumns() + " cols)");
                    layoutItem.setExpanded(true);
                    for (Object lc : layout.getChildren()) {
                        if (lc instanceof PropertyDef prop) {
                            TreeItem<String> propertyItem = new TreeItem<>(prop.getName());
                            layoutItem.getChildren().add(propertyItem);
                            outlineTargets.put(propertyItem, "property:" + prop.getName());
                        }
                    }
                    sectionItem.getChildren().add(layoutItem);
                }
            }

            treeRoot.getChildren().add(sectionItem);
        }

        outlineTree.setRoot(treeRoot);
    }

    // --- Help Tab ---

    /**
     * Show help for the configuration overview (no component selected).
     */
    public void showConfigurationHelp() {
        helpContent.getChildren().clear();

        if (model.getConfiguration() == null) {
            helpContent.getChildren().add(new Label("No configuration loaded."));
            return;
        }

        addHelpHeader("Configuration Overview");
        addHelpLine("Components: " + model.getConfiguration().getComponents().size());
        addHelpLine("Targets: " + String.join(", ", model.getConfiguration().getTargets()));
        addHelpLine("Active target: " + model.getActiveTarget());

        if (!model.getResolvedComponentRoots().isEmpty()) {
            addHelpHeader("Component Roots");
            for (Path root : model.getResolvedComponentRoots()) {
                addHelpLine("  " + root);
            }
        }

        if (!model.getUnresolvedComponents().isEmpty()) {
            addHelpHeader("Unresolved Components");
            for (String componentId : model.getUnresolvedComponents()) {
                Label unresolved = new Label("  \u26A0 " + componentId);
                unresolved.setWrapText(true);
                unresolved.setStyle("-fx-text-fill: #cc6600;");
                helpContent.getChildren().add(unresolved);
            }
        }

        if (!model.getWarnings().isEmpty()) {
            addHelpHeader("Warnings");
            for (String warning : model.getWarnings()) {
                Label w = new Label("  \u26A0 " + warning);
                w.setWrapText(true);
                w.setStyle("-fx-text-fill: #cc6600;");
                helpContent.getChildren().add(w);
            }
        }
    }

    /**
     * Show help for a specific component.
     */
    public void showComponentHelp(ComponentDefinition def) {
        helpContent.getChildren().clear();

        addHelpHeader(def.getName());
        addHelpLine("ID: " + def.getId());
        addHelpLine("Version: " + def.getVersion());

        if (def.getDescription() != null) {
            Label desc = new Label(def.getDescription());
            desc.setWrapText(true);
            desc.setPadding(new Insets(4, 0, 4, 0));
            helpContent.getChildren().add(desc);
        }

        if (!def.getCategories().isEmpty()) {
            addHelpHeader("Categories");
            for (String cat : def.getCategories()) {
                addHelpLine("  " + cat);
            }
        }

        if (!def.getRequires().isEmpty()) {
            addHelpHeader("Requires");
            for (var feat : def.getRequires()) {
                addHelpLine("  " + feat.getId());
            }
        }

        if (!def.getProvides().isEmpty()) {
            addHelpHeader("Provides");
            for (var feat : def.getProvides()) {
                String suffix = feat.isExclusive() ? " (exclusive)" : "";
                addHelpLine("  " + feat.getId() + suffix);
            }
        }

        addHelpHeader("Sections");
        for (SectionDef section : def.getSections()) {
            addHelpLine("  " + section.getName());
        }
    }

    public void showSectionHelp(SectionDef section) {
        helpContent.getChildren().clear();

        addHelpHeader(section.getName());
        if (section.getDescription() != null && !section.getDescription().isBlank()) {
            addHelpLine(section.getDescription());
        }
        addHelpHeader("Resolved State");
        addHelpLine("Expanded: " + section.isExpanded());
        addHelpLine("Editable: " + section.getEditable());
        addHelpLine("Visible: " + section.getVisible());
        addHelpLine("Children: " + section.getChildren().size());
    }

    /**
     * Show help for a specific property.
     */
    public void showPropertyHelp(PropertyDef prop) {
        helpContent.getChildren().clear();

        addHelpHeader(prop.getName());

        Label typeBadge = new Label(prop.getType().name().toLowerCase());
        typeBadge.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 2 6; " +
                "-fx-background-radius: 3; -fx-font-size: 11px;");
        helpContent.getChildren().add(typeBadge);

        addHelpLine(prop.getBrief());

        addHelpHeader("Constraints");
        addHelpLine("Required: " + prop.isRequired());
        addHelpLine("Editable: " + prop.getEditable());
        addHelpLine("Visible: " + prop.getVisible());
        addHelpLine("Default: " + (prop.getDefaultValue() != null ? prop.getDefaultValue() : "(none)"));

        if (prop.getIntMin() != null) addHelpLine("Min: " + prop.getIntMin());
        if (prop.getIntMax() != null) addHelpLine("Max: " + prop.getIntMax());
        if (prop.getStringRegex() != null) addHelpLine("Regex: " + prop.getStringRegex());
        if (prop.getTextMaxsize() != null) addHelpLine("Max size: " + prop.getTextMaxsize());
        if (prop.getEnumOf() != null) addHelpLine("Choices: " + prop.getEnumOf());
    }

    private void addHelpHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        label.setPadding(new Insets(4, 0, 2, 0));
        helpContent.getChildren().add(label);
    }

    private void addHelpLine(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12px;");
        helpContent.getChildren().add(label);
    }

    // --- Files Tab ---

    /**
     * Refresh the files tree from the configuration root.
     */
    public void refreshFiles() {
        Path configRoot = model.getConfigRoot();
        if (configRoot == null || !Files.isDirectory(configRoot)) {
            filesTree.setRoot(new TreeItem<>("(no configuration root)"));
            return;
        }

        TreeItem<String> treeRoot = new TreeItem<>(configRoot.getFileName().toString() + " [project]");
        treeRoot.setExpanded(true);
        buildFileTree(configRoot, configRoot, treeRoot);
        filesTree.setRoot(treeRoot);
    }

    private void buildFileTree(Path projectRoot, Path dir, TreeItem<String> parentItem) {
        try (Stream<Path> entries = Files.list(dir).sorted()) {
            entries.forEach(path -> {
                String name = path.getFileName().toString() + " [" + classifyPath(projectRoot, path) + "]";
                TreeItem<String> item = new TreeItem<>(name);
                parentItem.getChildren().add(item);
                if (Files.isDirectory(path)) {
                    buildFileTree(projectRoot, path, item);
                }
            });
        } catch (IOException ignored) {}
    }

    private String classifyPath(Path projectRoot, Path path) {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path relative;
        try {
            relative = normalizedProjectRoot.relativize(normalizedPath);
        } catch (Exception ignored) {
            return Files.isDirectory(path) ? "dir" : "file";
        }

        String rel = relative.toString().replace('\\', '/');
        if (rel.equals("generated") || rel.startsWith("generated/")) {
            return "generated";
        }
        if (rel.equals("chibiforge.xcfg") || rel.endsWith(".xcfg") || rel.equals("chibiforge_sources.json")) {
            return "managed";
        }
        return Files.isDirectory(path) ? "dir" : "user";
    }

    // --- Log Tab ---

    /**
     * Append text to the log.
     */
    public void appendLog(String text) {
        logArea.appendText(text + "\n");
        logArea.positionCaret(logArea.getLength());
    }

    /**
     * Clear the log.
     */
    public void clearLog() {
        logArea.clear();
    }

    /**
     * Switch to the Log tab.
     */
    public void showLogTab() {
        tabPane.getSelectionModel().select(3);
    }

    public VBox getRoot() { return root; }
    public TabPane getTabPane() { return tabPane; }
    public TextArea getLogArea() { return logArea; }
}
