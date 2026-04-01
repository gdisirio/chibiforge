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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Navigation breadcrumb bar: Components > ComponentName > Section > [index]
 * Each segment is clickable to navigate back to that level.
 */
public class BreadcrumbBar extends HBox {

    private final List<String> segments = new ArrayList<>();
    private Consumer<Integer> onNavigate;

    public BreadcrumbBar() {
        setSpacing(2);
        setPadding(new Insets(6, 8, 6, 8));
        getStyleClass().add("breadcrumb-bar");
        setPath("Components");
    }

    /**
     * Set the callback invoked when a breadcrumb segment is clicked.
     * The parameter is the segment index (0 = top level).
     */
    public void setOnNavigate(Consumer<Integer> onNavigate) {
        this.onNavigate = onNavigate;
    }

    /**
     * Set the breadcrumb path. Segments are separated by " > ".
     */
    public void setPath(String... path) {
        segments.clear();
        segments.addAll(List.of(path));
        rebuild();
    }

    /**
     * Navigate up one level. Returns true if navigation happened.
     */
    public boolean navigateUp() {
        if (segments.size() > 1) {
            if (onNavigate != null) {
                onNavigate.accept(segments.size() - 2);
            }
            return true;
        }
        return false;
    }

    public int getDepth() { return segments.size(); }

    private void rebuild() {
        getChildren().clear();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                Label sep = new Label(" \u203A ");
                sep.getStyleClass().add("breadcrumb-separator");
                getChildren().add(sep);
            }

            if (i < segments.size() - 1) {
                // Clickable link for non-terminal segments
                final int index = i;
                Hyperlink link = new Hyperlink(segments.get(i));
                link.getStyleClass().add("breadcrumb-link");
                link.setOnAction(e -> {
                    if (onNavigate != null) onNavigate.accept(index);
                });
                getChildren().add(link);
            } else {
                // Current segment (non-clickable)
                Label current = new Label(segments.get(i));
                current.getStyleClass().add("breadcrumb-current");
                getChildren().add(current);
            }
        }
    }
}
