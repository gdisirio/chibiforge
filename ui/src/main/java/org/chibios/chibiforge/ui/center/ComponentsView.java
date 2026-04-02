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

package org.chibios.chibiforge.ui.center;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.ui.model.AppModel;

import java.util.function.Consumer;

/**
 * Top-level center panel showing configured components as a card grid.
 * Provides add/remove controls and navigates into component configuration on double-click.
 */
public class ComponentsView {

    private final AppModel model;
    private final VBox root;
    private final FlowPane cardPane;
    private final Label countLabel;
    private final Label hintLabel;

    private String selectedComponentId;
    private Consumer<String> onComponentDoubleClick;
    private Runnable onAddSelected;
    private Consumer<String> onRemoveSelected;

    public ComponentsView(AppModel model) {
        this.model = model;

        // Mini-toolbar
        Button addBtn = new Button("Add Selected");
        addBtn.setOnAction(e -> {
            if (onAddSelected != null) onAddSelected.run();
        });
        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> removeSelectedComponent());
        countLabel = new Label("0 components configured");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox miniToolbar = new HBox(8, addBtn, removeBtn, spacer, countLabel);
        miniToolbar.setPadding(new Insets(6, 8, 6, 8));
        miniToolbar.setAlignment(Pos.CENTER_LEFT);
        miniToolbar.getStyleClass().add("mini-toolbar");

        // Card grid
        cardPane = new FlowPane();
        cardPane.setHgap(12);
        cardPane.setVgap(12);
        cardPane.setPadding(new Insets(12));
        cardPane.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(cardPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Hint
        hintLabel = new Label("Double-click to configure \u00b7 Select + Remove to delete");
        hintLabel.getStyleClass().add("hint-text");
        hintLabel.setPadding(new Insets(4, 8, 4, 8));

        root = new VBox(miniToolbar, scrollPane, hintLabel);
    }

    /**
     * Set callback for double-clicking a component card.
     * Parameter is the component ID.
     */
    public void setOnComponentDoubleClick(Consumer<String> handler) {
        this.onComponentDoubleClick = handler;
    }

    /**
     * Set callback for "Add Selected" button.
     */
    public void setOnAddSelected(Runnable handler) {
        this.onAddSelected = handler;
    }

    /**
     * Set callback for removing the selected component.
     */
    public void setOnRemoveSelected(Consumer<String> handler) {
        this.onRemoveSelected = handler;
    }

    /**
     * Rebuild the card grid from the current configuration.
     */
    public void refresh() {
        cardPane.getChildren().clear();
        selectedComponentId = null;

        if (model.getConfiguration() == null || model.getRegistry() == null) {
            countLabel.setText("0 components configured");
            return;
        }

        ComponentRegistry registry = model.getRegistry();
        int count = 0;

        for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
            String compId = entry.getComponentId();
            VBox card = createCard(compId, registry);
            cardPane.getChildren().add(card);
            count++;
        }

        countLabel.setText(count + " component(s) configured");
    }

    private VBox createCard(String componentId, ComponentRegistry registry) {
        String name = componentId;
        String version = "";
        try {
            ComponentContainer container = registry.lookup(componentId);
            ComponentDefinition def = container.loadDefinition();
            name = def.getName();
            version = def.getVersion();
        } catch (Exception ignored) {}

        Label iconLabel = new Label("\u2699"); // gear icon placeholder
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 32));
        iconLabel.setAlignment(Pos.CENTER);

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(176);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setStyle("-fx-text-alignment: center;");

        Label versionLabel = new Label(version);
        versionLabel.getStyleClass().add("version-label");
        versionLabel.setAlignment(Pos.CENTER);

        VBox card = new VBox(6, iconLabel, nameLabel, versionLabel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setPrefWidth(200);
        card.setMinHeight(120);
        card.getStyleClass().add("component-card");

        // Selection
        final String compId = componentId;
        card.setOnMouseClicked(e -> {
            // Deselect previous
            cardPane.getChildren().forEach(n -> n.getStyleClass().remove("card-selected"));
            card.getStyleClass().add("card-selected");
            selectedComponentId = compId;

            if (e.getClickCount() == 2 && onComponentDoubleClick != null) {
                onComponentDoubleClick.accept(compId);
            }
        });

        return card;
    }

    private void removeSelectedComponent() {
        if (selectedComponentId == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + selectedComponentId + " from configuration?\nThis will delete all its settings.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Remove Component");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                removeComponent(selectedComponentId);
            }
        });
    }

    private void removeComponent(String componentId) {
        if (onRemoveSelected != null) {
            onRemoveSelected.accept(componentId);
        }
    }

    public String getSelectedComponentId() { return selectedComponentId; }
    public VBox getRoot() { return root; }
}
