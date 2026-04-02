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

package org.chibios.chibiforge.ui.targets;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for managing build targets (add, rename, delete).
 * The "default" target cannot be renamed or deleted.
 */
public class ManageTargetsDialog extends Dialog<ManageTargetsDialog.Result> {

    public record Result(List<String> targets, Map<String, String> renamedTargets, List<String> deletedTargets) {
        public String remapTarget(String target) {
            if (target == null || target.isBlank()) {
                return "default";
            }
            if (deletedTargets.contains(target)) {
                return "default";
            }
            return renamedTargets.getOrDefault(target, target);
        }
    }

    private static final class TargetEntry {
        private final String originalName;
        private String currentName;

        private TargetEntry(String originalName, String currentName) {
            this.originalName = originalName;
            this.currentName = currentName;
        }

        @Override
        public String toString() {
            return currentName;
        }
    }

    private final ObservableList<TargetEntry> targets;
    private final ListView<TargetEntry> listView;
    private final List<String> originalTargets;

    public ManageTargetsDialog(List<String> currentTargets) {
        setTitle("Manage Targets");
        setHeaderText("Add, rename, or delete build targets.");
        setResizable(true);

        originalTargets = List.copyOf(currentTargets);
        targets = FXCollections.observableArrayList();
        for (String target : currentTargets) {
            targets.add(new TargetEntry(target, target));
        }
        listView = new ListView<>(targets);
        listView.setPrefHeight(200);
        listView.getSelectionModel().selectFirst();

        Button addBtn = new Button("Add...");
        Button renameBtn = new Button("Rename...");
        Button deleteBtn = new Button("Delete");

        addBtn.setOnAction(e -> {
            TextInputDialog input = new TextInputDialog();
            input.setTitle("Add Target");
            input.setHeaderText(null);
            input.setContentText("Target name:");
            input.showAndWait().ifPresent(name -> {
                String trimmed = name.trim();
                if (!trimmed.isEmpty() && containsTarget(trimmed)) {
                    return;
                }
                if (!trimmed.isEmpty()) {
                    targets.add(new TargetEntry(null, trimmed));
                }
            });
        });

        renameBtn.setOnAction(e -> {
            TargetEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null || "default".equals(selected.currentName)) return;

            TextInputDialog input = new TextInputDialog(selected.currentName);
            input.setTitle("Rename Target");
            input.setHeaderText(null);
            input.setContentText("New name:");
            input.showAndWait().ifPresent(name -> {
                String trimmed = name.trim();
                if (!trimmed.isEmpty() && !trimmed.equals(selected.currentName) && containsTarget(trimmed)) {
                    return;
                }
                if (!trimmed.isEmpty()) {
                    selected.currentName = trimmed;
                    listView.refresh();
                }
            });
        });

        deleteBtn.setOnAction(e -> {
            TargetEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null || "default".equals(selected.currentName)) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete target '" + selected.currentName + "'?\nAll per-target overrides for this target will be discarded.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    targets.remove(selected);
                }
            });
        });

        // Disable rename/delete for "default"
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean isDefault = sel != null && "default".equals(sel.currentName);
            renameBtn.setDisable(isDefault || sel == null);
            deleteBtn.setDisable(isDefault || sel == null);
        });
        renameBtn.setDisable(true);
        deleteBtn.setDisable(true);

        VBox buttons = new VBox(4, addBtn, renameBtn, deleteBtn);
        buttons.setPadding(new Insets(0, 0, 0, 8));

        HBox content = new HBox(8, listView, buttons);
        HBox.setHgrow(listView, Priority.ALWAYS);
        content.setPadding(new Insets(8));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(btn -> btn == ButtonType.OK ? buildResult() : null);
    }

    private boolean containsTarget(String name) {
        return targets.stream().anyMatch(entry -> entry.currentName.equals(name));
    }

    private Result buildResult() {
        List<String> finalTargets = new ArrayList<>();
        Map<String, String> renamedTargets = new LinkedHashMap<>();
        List<String> deletedTargets = new ArrayList<>();

        for (TargetEntry entry : targets) {
            finalTargets.add(entry.currentName);
            if (entry.originalName != null && !entry.originalName.equals(entry.currentName)) {
                renamedTargets.put(entry.originalName, entry.currentName);
            }
        }

        for (String originalTarget : originalTargets) {
            boolean stillPresent = targets.stream().anyMatch(entry -> originalTarget.equals(entry.originalName));
            if (!stillPresent) {
                deletedTargets.add(originalTarget);
            }
        }

        return new Result(List.copyOf(finalTargets), Map.copyOf(renamedTargets), List.copyOf(deletedTargets));
    }
}
