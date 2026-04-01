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

package org.chibios.chibiforge.ui.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.ui.model.LiveDataModel;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates JavaFX widgets for property definitions.
 * Each widget reads its initial value from the DOM and writes back on edit.
 * Supports @ref: resolution for defaults/constraints and @cond: for visibility/editability.
 */
public class PropertyWidgetFactory {

    private LiveDataModel liveModel;
    private Consumer<String> onDomUpdate;

    // Track rows for re-evaluation
    private final List<ConditionBinding> conditionBindings = new ArrayList<>();

    public void setLiveModel(LiveDataModel liveModel) {
        this.liveModel = liveModel;
    }

    /**
     * Set callback invoked after a property value is written to the DOM.
     * The parameter is the property name that changed.
     */
    public void setOnDomUpdate(Consumer<String> onDomUpdate) {
        this.onDomUpdate = onDomUpdate;
    }

    /**
     * Create a labeled property row: label on left, widget on right.
     */
    public Node createPropertyRow(PropertyDef prop, Element parentEl) {
        // Label side
        Label nameLabel = new Label(prop.getName());
        nameLabel.getStyleClass().add("property-name");
        Label briefLabel = new Label(prop.getBrief());
        briefLabel.getStyleClass().add("property-brief");
        briefLabel.setWrapText(true);
        VBox labelBox = new VBox(2, nameLabel, briefLabel);
        labelBox.setMinWidth(200);
        labelBox.setPrefWidth(250);

        // Widget side
        Node widget = createWidget(prop, parentEl);
        HBox.setHgrow(widget, Priority.ALWAYS);

        HBox row = new HBox(12, labelBox, widget);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        row.getStyleClass().add("property-row");

        // Editability: static or @cond:
        if (prop.hasEditableCondition()) {
            conditionBindings.add(new ConditionBinding(
                    prop.getEditableCondition(), widget, ConditionBinding.Type.EDITABLE));
        } else if (!prop.isEditable()) {
            widget.setDisable(true);
        }

        // Visibility: static or @cond:
        if (prop.hasVisibleCondition()) {
            conditionBindings.add(new ConditionBinding(
                    prop.getVisibleCondition(), row, ConditionBinding.Type.VISIBLE));
        } else if (!prop.isVisible()) {
            row.setVisible(false);
            row.setManaged(false);
        }

        return row;
    }

    /**
     * Re-evaluate all @cond: expressions after a DOM update.
     */
    public void reEvaluateConditions() {
        if (liveModel == null) return;
        liveModel.rebuildXPathDoc();

        for (ConditionBinding binding : conditionBindings) {
            boolean result = liveModel.evaluateCondition(binding.expression);
            if (binding.type == ConditionBinding.Type.VISIBLE) {
                binding.node.setVisible(result);
                binding.node.setManaged(result);
            } else {
                binding.node.setDisable(!result);
            }
        }
    }

    /**
     * Clear tracked condition bindings (call when loading a new component).
     */
    public void clearBindings() {
        conditionBindings.clear();
    }

    private Node createWidget(PropertyDef prop, Element parentEl) {
        // Resolve @ref: in default value
        String resolvedDefault = resolveRef(prop.getDefaultValue());
        String currentValue = getDomValue(prop.getName(), parentEl, resolvedDefault);

        return switch (prop.getType()) {
            case BOOL -> createBoolWidget(prop, parentEl, currentValue);
            case STRING -> createStringWidget(prop, parentEl, currentValue);
            case INT -> createIntWidget(prop, parentEl, currentValue);
            case ENUM -> createEnumWidget(prop, parentEl, currentValue);
            case TEXT -> createTextWidget(prop, parentEl, currentValue);
            case LIST -> createListPlaceholder(prop);
        };
    }

    private Node createBoolWidget(PropertyDef prop, Element parentEl, String value) {
        CheckBox cb = new CheckBox();
        cb.setSelected("true".equalsIgnoreCase(value));
        cb.setOnAction(e -> {
            setDomValue(prop.getName(), parentEl, String.valueOf(cb.isSelected()));
            fireDomUpdate(prop.getName());
        });
        return cb;
    }

    private Node createStringWidget(PropertyDef prop, Element parentEl, String value) {
        TextField field = new TextField(value);
        field.setPromptText(prop.getBrief());
        String regex = resolveRef(prop.getStringRegex());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = field.getText();
                if (regex != null && !text.isEmpty() && !text.matches(regex)) {
                    field.getStyleClass().add("field-error");
                    return;
                }
                field.getStyleClass().remove("field-error");
                setDomValue(prop.getName(), parentEl, text);
                fireDomUpdate(prop.getName());
            }
        });
        return field;
    }

    private Node createIntWidget(PropertyDef prop, Element parentEl, String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(120);
        // Resolve @ref: in constraints
        String minStr = resolveRef(prop.getIntMin());
        String maxStr = resolveRef(prop.getIntMax());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = field.getText().trim();
                try {
                    int val = Integer.parseInt(text);
                    if (minStr != null && val < Integer.parseInt(minStr)) {
                        field.getStyleClass().add("field-error");
                        return;
                    }
                    if (maxStr != null && val > Integer.parseInt(maxStr)) {
                        field.getStyleClass().add("field-error");
                        return;
                    }
                    field.getStyleClass().remove("field-error");
                    setDomValue(prop.getName(), parentEl, text);
                    fireDomUpdate(prop.getName());
                } catch (NumberFormatException e) {
                    field.getStyleClass().add("field-error");
                }
            }
        });
        return field;
    }

    private Node createEnumWidget(PropertyDef prop, Element parentEl, String value) {
        ComboBox<String> combo = new ComboBox<>();
        // Resolve @ref: in enum_of
        String enumOf = resolveRef(prop.getEnumOf());
        if (enumOf != null) {
            for (String choice : enumOf.split(",")) {
                combo.getItems().add(choice.trim());
            }
        }
        combo.setValue(value);
        combo.setOnAction(e -> {
            String selected = combo.getValue();
            if (selected != null) {
                setDomValue(prop.getName(), parentEl, selected);
                fireDomUpdate(prop.getName());
            }
        });
        return combo;
    }

    private Node createTextWidget(PropertyDef prop, Element parentEl, String value) {
        TextArea area = new TextArea(value);
        area.setPrefRowCount(5);
        area.setStyle("-fx-font-family: monospace;");
        String maxSizeStr = resolveRef(prop.getTextMaxsize());
        area.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = area.getText();
                if (maxSizeStr != null) {
                    int max = Integer.parseInt(maxSizeStr);
                    if (text.length() > max) {
                        area.getStyleClass().add("field-error");
                        return;
                    }
                }
                area.getStyleClass().remove("field-error");
                setDomValue(prop.getName(), parentEl, text);
                fireDomUpdate(prop.getName());
            }
        });
        return area;
    }

    private Node createListPlaceholder(PropertyDef prop) {
        Label label = new Label("[List: " + prop.getName() + " — M6]");
        label.getStyleClass().add("placeholder-text");
        return label;
    }

    private String resolveRef(String value) {
        if (value == null) return null;
        if (liveModel != null) return liveModel.resolveRef(value);
        return value;
    }

    private String getDomValue(String propertyName, Element parentEl, String defaultValue) {
        NodeList nodes = parentEl.getElementsByTagName(propertyName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return defaultValue != null ? defaultValue : "";
    }

    private void setDomValue(String propertyName, Element parentEl, String value) {
        NodeList nodes = parentEl.getElementsByTagName(propertyName);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(value);
        } else {
            Element newEl = parentEl.getOwnerDocument().createElement(propertyName);
            newEl.setTextContent(value);
            parentEl.appendChild(newEl);
        }
    }

    private void fireDomUpdate(String propertyName) {
        if (onDomUpdate != null) onDomUpdate.accept(propertyName);
    }

    /** Tracks a @cond: expression binding to a node. */
    private static class ConditionBinding {
        enum Type { VISIBLE, EDITABLE }
        final String expression;
        final Node node;
        final Type type;

        ConditionBinding(String expression, Node node, Type type) {
            this.expression = expression;
            this.node = node;
            this.type = type;
        }
    }
}
