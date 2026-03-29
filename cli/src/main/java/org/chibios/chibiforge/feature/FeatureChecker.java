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

package org.chibios.chibiforge.feature;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.FeatureDef;

import java.util.*;

/**
 * Checks soft feature dependencies across components in a configuration.
 * Produces warnings for unresolved requirements and exclusive feature conflicts.
 */
public class FeatureChecker {

    /**
     * Check feature dependencies and return a list of warning messages.
     * @param definitions all component definitions in the configuration
     * @return list of warning strings (empty if all OK)
     */
    public List<String> check(List<ComponentDefinition> definitions) {
        List<String> warnings = new ArrayList<>();

        // Collect provided features: featureId -> list of providing component IDs
        Map<String, List<String>> provided = new HashMap<>();
        Map<String, Boolean> exclusiveFlags = new HashMap<>();

        for (ComponentDefinition def : definitions) {
            for (FeatureDef feature : def.getProvides()) {
                provided.computeIfAbsent(feature.getId(), k -> new ArrayList<>()).add(def.getId());
                if (feature.isExclusive()) {
                    exclusiveFlags.put(feature.getId(), true);
                }
            }
        }

        // Check required features
        for (ComponentDefinition def : definitions) {
            for (FeatureDef required : def.getRequires()) {
                if (!provided.containsKey(required.getId())) {
                    warnings.add("Component '" + def.getId() + "' requires feature '" +
                            required.getId() + "' but no component provides it");
                }
            }
        }

        // Check exclusive feature conflicts
        for (Map.Entry<String, List<String>> entry : provided.entrySet()) {
            String featureId = entry.getKey();
            List<String> providers = entry.getValue();
            if (Boolean.TRUE.equals(exclusiveFlags.get(featureId)) && providers.size() > 1) {
                warnings.add("Exclusive feature '" + featureId + "' provided by multiple components: " +
                        String.join(", ", providers));
            }
        }

        return warnings;
    }
}
