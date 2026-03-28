package org.chibios.chibiforge.datamodel;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IdNormalizerTest {

    @Test
    void specExample_dotSeparatedId() {
        assertThat(IdNormalizer.normalize("org.chibios.hal.stm32f4xx"))
                .isEqualTo("org_chibios_hal_stm32f4xx");
    }

    @Test
    void specExample_mixedCaseWithDashAndUnderscore() {
        assertThat(IdNormalizer.normalize("HAL-Core_v2"))
                .isEqualTo("hal_core_v2");
    }

    @Test
    void specExample_consecutiveDots() {
        assertThat(IdNormalizer.normalize("my..strange...id"))
                .isEqualTo("my_strange_id");
    }

    @Test
    void simpleAlphanumeric_unchanged() {
        assertThat(IdNormalizer.normalize("hello")).isEqualTo("hello");
        assertThat(IdNormalizer.normalize("test123")).isEqualTo("test123");
    }

    @Test
    void underscorePreserved() {
        assertThat(IdNormalizer.normalize("a_b_c")).isEqualTo("a_b_c");
    }

    @Test
    void emptyString() {
        assertThat(IdNormalizer.normalize("")).isEqualTo("");
    }

    @Test
    void nullInput() {
        assertThat(IdNormalizer.normalize(null)).isNull();
    }

    @Test
    void allSpecialChars() {
        assertThat(IdNormalizer.normalize("...")).isEqualTo("_");
    }

    @Test
    void leadingAndTrailingDots() {
        assertThat(IdNormalizer.normalize(".foo.bar."))
                .isEqualTo("_foo_bar_");
    }

    @Test
    void uppercaseOnly() {
        assertThat(IdNormalizer.normalize("HELLO"))
                .isEqualTo("hello");
    }

    @Test
    void mixedSeparators() {
        assertThat(IdNormalizer.normalize("a.b-c_d"))
                .isEqualTo("a_b_c_d");
    }
}
