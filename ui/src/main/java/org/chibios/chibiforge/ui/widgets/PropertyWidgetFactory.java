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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.function.Consumer;

/**
 * Creates JavaFX widgets for property definitions.
 * Each widget reads its initial value from the DOM and writes back on edit.
 */
public class PropertyWidgetFactory {

    private Consumer<String> onDomUpdate;

    /**
     * Set callback invoked after a property value is written to the DOM.
     * The parameter is the property name that changed.
     */
    public void setOnDomUpdate(Consumer<String> onDomUpdate) {
        this.onDomUpdate = onDomUpdate;
    }

    /**
     * Create a labeled property row: label on left, widget on right.
     *
     * @param prop      the property definition
     * @param parentEl  the DOM element containing this property's value element
     * @return the row node
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

        // Editability
        if (!prop.isEditable() && !prop.hasEditableCondition()) {
            widget.setDisable(true);
        }

        return row;
    }

    /**
     * Create the appropriate input widget for a property type.
     */
    private Node createWidget(PropertyDef prop, Element parentEl) {
        String currentValue = getDomValue(prop.getName(), parentEl, prop.getDefaultValue());

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
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = field.getText();
                // Validate regex
                if (prop.getStringRegex() != null && !text.isEmpty()
                        && !text.matches(prop.getStringRegex())) {
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
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = field.getText().trim();
                try {
                    int val = Integer.parseInt(text);
                    if (prop.getIntMin() != null && val < Integer.parseInt(prop.getIntMin())) {
                        field.getStyleClass().add("field-error");
                        return;
                    }
                    if (prop.getIntMax() != null && val > Integer.parseInt(prop.getIntMax())) {
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
        if (prop.getEnumOf() != null) {
            for (String choice : prop.getEnumOf().split(",")) {
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
        area.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = area.getText();
                if (prop.getTextMaxsize() != null) {
                    int max = Integer.parseInt(prop.getTextMaxsize());
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

    /**
     * Read a property value from the DOM, or return the default.
     */
    private String getDomValue(String propertyName, Element parentEl, String defaultValue) {
        NodeList nodes = parentEl.getElementsByTagName(propertyName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return defaultValue != null ? defaultValue : "";
    }

    /**
     * Write a property value to the DOM. Creates the element if missing.
     */
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
}
