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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureCheckerTest {

    private final FeatureChecker checker = new FeatureChecker();

    @Test
    void noFeatures_noWarnings() {
        ComponentDefinition def = makeDef("comp.a", List.of(), List.of());
        assertThat(checker.check(List.of(def))).isEmpty();
    }

    @Test
    void satisfiedRequirement_noWarnings() {
        ComponentDefinition provider = makeDef("comp.a", List.of(),
                List.of(new FeatureDef("features.core", false)));
        ComponentDefinition consumer = makeDef("comp.b",
                List.of(new FeatureDef("features.core", false)), List.of());
        assertThat(checker.check(List.of(provider, consumer))).isEmpty();
    }

    @Test
    void unsatisfiedRequirement_producesWarning() {
        ComponentDefinition consumer = makeDef("comp.b",
                List.of(new FeatureDef("features.core", false)), List.of());
        List<String> warnings = checker.check(List.of(consumer));
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("comp.b").contains("features.core");
    }

    @Test
    void exclusiveFeature_singleProvider_noWarning() {
        ComponentDefinition a = makeDef("comp.a", List.of(),
                List.of(new FeatureDef("features.platform", true)));
        assertThat(checker.check(List.of(a))).isEmpty();
    }

    @Test
    void exclusiveFeature_multipleProviders_producesWarning() {
        ComponentDefinition a = makeDef("comp.a", List.of(),
                List.of(new FeatureDef("features.platform", true)));
        ComponentDefinition b = makeDef("comp.b", List.of(),
                List.of(new FeatureDef("features.platform", true)));
        List<String> warnings = checker.check(List.of(a, b));
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("features.platform").contains("comp.a").contains("comp.b");
    }

    @Test
    void mixedWarnings() {
        ComponentDefinition a = makeDef("comp.a",
                List.of(new FeatureDef("features.missing", false)),
                List.of(new FeatureDef("features.exclusive", true)));
        ComponentDefinition b = makeDef("comp.b", List.of(),
                List.of(new FeatureDef("features.exclusive", true)));
        List<String> warnings = checker.check(List.of(a, b));
        assertThat(warnings).hasSize(2);
    }

    private static ComponentDefinition makeDef(String id, List<FeatureDef> requires, List<FeatureDef> provides) {
        return new ComponentDefinition(id, id, "1.0.0", false, false, "test",
                List.of(), List.of("Test"), requires, List.of(), provides, List.of());
    }
}
