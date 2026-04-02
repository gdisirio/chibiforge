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
import java.util.Map;
import java.util.function.Consumer;

/**
 * Creates JavaFX widgets for property definitions.
 * Supports @ref:/@cond: resolution and multi-target property values.
 */
public class PropertyWidgetFactory {

    private LiveDataModel liveModel;
    private Consumer<String> onDomUpdate;
    private String activeTarget = "default";

    private final List<ConditionBinding> conditionBindings = new ArrayList<>();
    private final List<TargetBinding> targetBindings = new ArrayList<>();

    public void setLiveModel(LiveDataModel liveModel) { this.liveModel = liveModel; }
    public void setOnDomUpdate(Consumer<String> onDomUpdate) { this.onDomUpdate = onDomUpdate; }
    public void setActiveTarget(String target) { this.activeTarget = target; }

    /**
     * Create a labeled property row with multi-target indicator.
     */
    public Node createPropertyRow(PropertyDef prop, Element parentEl) {
        // Label
        Label nameLabel = new Label(prop.getName());
        nameLabel.getStyleClass().add("property-name");
        Label briefLabel = new Label(prop.getBrief());
        briefLabel.getStyleClass().add("property-brief");
        briefLabel.setWrapText(true);
        VBox labelBox = new VBox(2, nameLabel, briefLabel);
        labelBox.setMinWidth(200);
        labelBox.setPrefWidth(250);

        // Ensure property element exists in DOM
        Element propEl = findOrCreatePropertyElement(parentEl, prop.getName(), prop.getDefaultValue());

        // Widget
        Node widget = createWidget(prop, propEl);
        HBox.setHgrow(widget, Priority.ALWAYS);

        // Multi-target indicator
        boolean isMulti = MultiTargetHelper.isMultiTarget(propEl);
        Label indicator = new Label(isMulti ? "\u25C6" : "\u25C7");
        indicator.getStyleClass().add(isMulti ? "mt-indicator-active" : "mt-indicator");
        indicator.setCursor(javafx.scene.Cursor.HAND);
        Tooltip tooltip = buildTargetTooltip(propEl);
        Tooltip.install(indicator, tooltip);

        indicator.setOnMouseClicked(e -> {
            if (MultiTargetHelper.isMultiTarget(propEl)) {
                // Demote
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Discard all per-target overrides and use only the default value?",
                        ButtonType.OK, ButtonType.CANCEL);
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.OK) {
                        MultiTargetHelper.demoteToSingleTarget(propEl);
                        indicator.setText("\u25C7");
                        indicator.getStyleClass().setAll("mt-indicator");
                        refreshWidget(widget, prop, propEl);
                        fireDomUpdate(prop.getName());
                    }
                });
            } else {
                // Promote
                MultiTargetHelper.promoteToMultiTarget(propEl);
                indicator.setText("\u25C6");
                indicator.getStyleClass().setAll("mt-indicator-active");
                fireDomUpdate(prop.getName());
            }
            Tooltip.install(indicator, buildTargetTooltip(propEl));
        });

        // Track for target switching
        targetBindings.add(new TargetBinding(prop, propEl, widget, indicator));

        HBox row = new HBox(8, labelBox, widget, indicator);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        row.getStyleClass().add("property-row");

        // Editability
        if (prop.hasEditableCondition()) {
            conditionBindings.add(new ConditionBinding(
                    prop.getEditableCondition(), widget, ConditionBinding.Type.EDITABLE));
        } else if (!prop.isEditable()) {
            widget.setDisable(true);
        }

        // Visibility
        if (prop.hasVisibleCondition()) {
            conditionBindings.add(new ConditionBinding(
                    prop.getVisibleCondition(), row, ConditionBinding.Type.VISIBLE));
        } else if (!prop.isVisible()) {
            row.setVisible(false);
            row.setManaged(false);
        }

        // Fallback style for multi-target
        if (isMulti && MultiTargetHelper.isUsingFallback(propEl, activeTarget)) {
            widget.setStyle("-fx-opacity: 0.7; -fx-font-style: italic;");
        }

        return row;
    }

    /**
     * Register @cond: bindings for a section's visibility and editability.
     */
    public void registerSectionConditions(org.chibios.chibiforge.component.SectionDef section,
                                           Node sectionPane, Node sectionContent) {
        if (section.hasVisibleCondition()) {
            conditionBindings.add(new ConditionBinding(
                    section.getVisibleCondition(), sectionPane, ConditionBinding.Type.VISIBLE));
        }
        if (section.hasEditableCondition()) {
            conditionBindings.add(new ConditionBinding(
                    section.getEditableCondition(), sectionContent, ConditionBinding.Type.EDITABLE));
        }
    }

    /**
     * Re-evaluate @cond: expressions.
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
     * Refresh all widget values for a new active target.
     */
    public void refreshForTarget(String newTarget) {
        this.activeTarget = newTarget;
        for (TargetBinding tb : targetBindings) {
            refreshWidget(tb.widget, tb.prop, tb.propElement);

            boolean isMulti = MultiTargetHelper.isMultiTarget(tb.propElement);
            tb.indicator.setText(isMulti ? "\u25C6" : "\u25C7");
            tb.indicator.getStyleClass().setAll(isMulti ? "mt-indicator-active" : "mt-indicator");
            Tooltip.install(tb.indicator, buildTargetTooltip(tb.propElement));

            if (isMulti && MultiTargetHelper.isUsingFallback(tb.propElement, activeTarget)) {
                tb.widget.setStyle("-fx-opacity: 0.7; -fx-font-style: italic;");
            } else {
                tb.widget.setStyle("");
            }
        }
    }

    public void clearBindings() {
        conditionBindings.clear();
        targetBindings.clear();
    }

    // --- Widget creation ---

    private Node createWidget(PropertyDef prop, Element propEl) {
        String resolvedDefault = resolveRef(prop.getDefaultValue());
        String currentValue = getResolvedValue(propEl, resolvedDefault);

        return switch (prop.getType()) {
            case BOOL -> createBoolWidget(prop, propEl, currentValue);
            case STRING -> createStringWidget(prop, propEl, currentValue);
            case INT -> createIntWidget(prop, propEl, currentValue);
            case ENUM -> createEnumWidget(prop, propEl, currentValue);
            case TEXT -> createTextWidget(prop, propEl, currentValue);
            case LIST -> createListPlaceholder(prop);
        };
    }

    private void refreshWidget(Node widget, PropertyDef prop, Element propEl) {
        String value = getResolvedValue(propEl, resolveRef(prop.getDefaultValue()));
        if (widget instanceof CheckBox cb) {
            cb.setSelected("true".equalsIgnoreCase(value));
        } else if (widget instanceof TextField tf) {
            tf.setText(value);
        } else if (widget instanceof ComboBox<?>) {
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) widget;
            combo.setValue(value);
        } else if (widget instanceof TextArea ta) {
            ta.setText(value);
        }
    }

    private Node createBoolWidget(PropertyDef prop, Element propEl, String value) {
        CheckBox cb = new CheckBox();
        cb.setSelected("true".equalsIgnoreCase(value));
        cb.setOnAction(e -> {
            writeValue(propEl, String.valueOf(cb.isSelected()));
            fireDomUpdate(prop.getName());
        });
        return cb;
    }

    private Node createStringWidget(PropertyDef prop, Element propEl, String value) {
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
                writeValue(propEl, text);
                fireDomUpdate(prop.getName());
            }
        });
        return field;
    }

    private Node createIntWidget(PropertyDef prop, Element propEl, String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(120);
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
                    writeValue(propEl, text);
                    fireDomUpdate(prop.getName());
                } catch (NumberFormatException e) {
                    field.getStyleClass().add("field-error");
                }
            }
        });
        return field;
    }

    private Node createEnumWidget(PropertyDef prop, Element propEl, String value) {
        ComboBox<String> combo = new ComboBox<>();
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
                writeValue(propEl, selected);
                fireDomUpdate(prop.getName());
            }
        });
        return combo;
    }

    private Node createTextWidget(PropertyDef prop, Element propEl, String value) {
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
                writeValue(propEl, text);
                fireDomUpdate(prop.getName());
            }
        });
        return area;
    }

    private Node createListPlaceholder(PropertyDef prop) {
        Label label = new Label("[List: " + prop.getName() + " — use table above]");
        label.getStyleClass().add("placeholder-text");
        return label;
    }

    // --- DOM helpers ---

    private String getResolvedValue(Element propEl, String defaultValue) {
        if (MultiTargetHelper.isMultiTarget(propEl)) {
            return MultiTargetHelper.getValue(propEl, activeTarget);
        }
        String text = propEl.getTextContent().trim();
        return text.isEmpty() ? (defaultValue != null ? defaultValue : "") : text;
    }

    private void writeValue(Element propEl, String value) {
        if (MultiTargetHelper.isMultiTarget(propEl)) {
            MultiTargetHelper.setValue(propEl, activeTarget, value);
        } else {
            propEl.setTextContent(value);
        }
    }

    private Element findOrCreatePropertyElement(Element parentEl, String name, String defaultValue) {
        NodeList nodes = parentEl.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        Element newEl = parentEl.getOwnerDocument().createElement(name);
        newEl.setTextContent(defaultValue != null ? defaultValue : "");
        parentEl.appendChild(newEl);
        return newEl;
    }

    private Tooltip buildTargetTooltip(Element propEl) {
        if (!MultiTargetHelper.isMultiTarget(propEl)) {
            return new Tooltip("Single-target. Click to enable per-target values.");
        }
        Map<String, String> values = MultiTargetHelper.getAllTargetValues(propEl);
        StringBuilder sb = new StringBuilder("Multi-target values:\n");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String marker = entry.getKey().equals(activeTarget) ? " \u25C0" : "";
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(marker).append("\n");
        }
        return new Tooltip(sb.toString().trim());
    }

    private String resolveRef(String value) {
        if (value == null) return null;
        if (liveModel != null) return liveModel.resolveRef(value);
        return value;
    }

    private void fireDomUpdate(String propertyName) {
        if (onDomUpdate != null) onDomUpdate.accept(propertyName);
    }

    // --- Binding records ---

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

    private static class TargetBinding {
        final PropertyDef prop;
        final Element propElement;
        final Node widget;
        final Label indicator;
        TargetBinding(PropertyDef prop, Element propElement, Node widget, Label indicator) {
            this.prop = prop;
            this.propElement = propElement;
            this.widget = widget;
            this.indicator = indicator;
        }
    }
}
