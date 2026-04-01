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

package org.chibios.chibiforge.ui.palette;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.ui.model.AppModel;

import java.util.*;

/**
 * Left panel showing all available components grouped by category.
 * Components already in the configuration are shown with a checkmark.
 */
public class ComponentPalette {

    private final AppModel model;
    private final VBox root;
    private final TextField filterField;
    private final TreeView<String> treeView;
    private final CheckBox showHidden;

    // Maps display text back to component ID
    private final Map<TreeItem<String>, String> itemToComponentId = new HashMap<>();

    public ComponentPalette(AppModel model) {
        this.model = model;

        Label header = new Label("Available Components");
        header.getStyleClass().add("panel-header");
        header.setPadding(new Insets(8));

        filterField = new TextField();
        filterField.setPromptText("Filter components...");
        filterField.setPadding(new Insets(4));
        filterField.textProperty().addListener((obs, old, text) -> refresh());

        treeView = new TreeView<>();
        treeView.setShowRoot(false);
        treeView.setRoot(new TreeItem<>("Components"));
        VBox.setVgrow(treeView, Priority.ALWAYS);

        showHidden = new CheckBox("Show hidden");
        showHidden.setPadding(new Insets(4, 8, 8, 8));
        showHidden.setOnAction(e -> refresh());

        root = new VBox(header, filterField, treeView, showHidden);
        root.setPrefWidth(250);
        root.setMinWidth(200);
        root.getStyleClass().add("palette-panel");
    }

    /**
     * Rebuild the tree from the current registry and configuration state.
     */
    public void refresh() {
        TreeItem<String> treeRoot = new TreeItem<>("Components");
        treeRoot.setExpanded(true);
        itemToComponentId.clear();

        ComponentRegistry registry = model.getRegistry();
        if (registry == null) {
            treeView.setRoot(treeRoot);
            return;
        }

        Set<String> configured = model.getConfiguredComponentIds();
        String filter = filterField.getText() != null ? filterField.getText().toLowerCase() : "";
        boolean showHiddenComponents = showHidden.isSelected();

        // Group components by category
        Map<String, List<ComponentContainer>> categoryMap = new TreeMap<>();

        for (ComponentContainer container : registry.all()) {
            try {
                ComponentDefinition def = container.loadDefinition();

                // Filter hidden
                if (def.isHidden() && !showHiddenComponents) continue;

                // Filter by text
                if (!filter.isEmpty() && !def.getName().toLowerCase().contains(filter)
                        && !def.getId().toLowerCase().contains(filter)) continue;

                // Add to each category
                for (String category : def.getCategories()) {
                    categoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(container);
                }

                // If no categories, put under "Uncategorized"
                if (def.getCategories().isEmpty()) {
                    categoryMap.computeIfAbsent("Uncategorized", k -> new ArrayList<>()).add(container);
                }
            } catch (Exception ignored) {
                // Skip components that fail to load
            }
        }

        // Build tree from category paths (split on "/")
        for (Map.Entry<String, List<ComponentContainer>> entry : categoryMap.entrySet()) {
            String categoryPath = entry.getKey();
            String[] segments = categoryPath.split("/");

            TreeItem<String> parent = treeRoot;
            for (String segment : segments) {
                parent = findOrCreateChild(parent, segment);
            }

            for (ComponentContainer container : entry.getValue()) {
                try {
                    ComponentDefinition def = container.loadDefinition();
                    boolean isConfigured = configured.contains(def.getId());
                    String label = (isConfigured ? "\u2713 " : "") + def.getName();
                    TreeItem<String> item = new TreeItem<>(label);
                    parent.getChildren().add(item);
                    itemToComponentId.put(item, def.getId());
                } catch (Exception ignored) {}
            }
        }

        // Expand all category nodes
        expandAll(treeRoot);
        treeView.setRoot(treeRoot);
    }

    private TreeItem<String> findOrCreateChild(TreeItem<String> parent, String name) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(name) && !itemToComponentId.containsKey(child)) {
                return child;
            }
        }
        TreeItem<String> child = new TreeItem<>(name);
        parent.getChildren().add(child);
        return child;
    }

    private void expandAll(TreeItem<String> item) {
        item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) {
            if (!child.isLeaf()) expandAll(child);
        }
    }

    /**
     * Returns the component ID for the currently selected tree item, or null.
     */
    public String getSelectedComponentId() {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        return selected != null ? itemToComponentId.get(selected) : null;
    }

    public VBox getRoot() { return root; }
    public TreeView<String> getTreeView() { return treeView; }
}
