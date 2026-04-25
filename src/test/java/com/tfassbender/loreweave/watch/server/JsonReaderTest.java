package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonReaderTest {

    @Test
    void parseObjectFromBlankReturnsEmpty() {
        assertThat(JsonReader.parseObject("")).isEmpty();
        assertThat(JsonReader.parseObject(null)).isEmpty();
    }

    @Test
    void parseFlatObjectKeysAreStrings() {
        Map<String, Object> m = JsonReader.parseObject(
                "{\"a\":1,\"b\":\"x\",\"c\":true,\"d\":null}");
        assertThat(m).containsEntry("a", 1).containsEntry("b", "x")
                .containsEntry("c", true);
        assertThat(m).containsKey("d");
        assertThat(m.get("d")).isNull();
    }

    @Test
    void parseNestedObjectsAndArrays() {
        Map<String, Object> m = JsonReader.parseObject(
                "{\"inner\":{\"k\":42},\"arr\":[\"a\",\"b\"]}");
        assertThat(m.get("inner")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) m.get("inner")).get("k")).isEqualTo(42);
        assertThat(m.get("arr")).isEqualTo(List.of("a", "b"));
    }

    @Test
    void parseObjectRejectsNonObjectTopLevel() {
        assertThatThrownBy(() -> JsonReader.parseObject("[1,2,3]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected JSON object");
    }

    @Test
    void intOrCoercesNumericStringsAndFallsBackOnNonNumeric() {
        Map<String, Object> m = Map.of("n", 7, "s", "42", "bogus", "nope");
        assertThat(JsonReader.intOr(m, "n", -1)).isEqualTo(7);
        assertThat(JsonReader.intOr(m, "s", -1)).isEqualTo(42);
        assertThat(JsonReader.intOr(m, "bogus", -1)).isEqualTo(-1);
        assertThat(JsonReader.intOr(m, "missing", -1)).isEqualTo(-1);
    }

    @Test
    void longOrAcceptsLargeValues() {
        Map<String, Object> m = Map.of("big", 9_999_999_999L, "s", "1234567890123");
        assertThat(JsonReader.longOr(m, "big", 0L)).isEqualTo(9_999_999_999L);
        assertThat(JsonReader.longOr(m, "s",   0L)).isEqualTo(1_234_567_890_123L);
        assertThat(JsonReader.longOr(m, "miss", 7L)).isEqualTo(7L);
    }

    @Test
    void boolOrAcceptsBooleansAndStrings() {
        Map<String, Object> m = Map.of("a", true, "b", "false", "c", "TRUE", "d", "yes");
        assertThat(JsonReader.boolOr(m, "a", false)).isTrue();
        assertThat(JsonReader.boolOr(m, "b", true)).isFalse();
        assertThat(JsonReader.boolOr(m, "c", false)).isTrue();
        assertThat(JsonReader.boolOr(m, "d", false)).isFalse(); // unknown string falls back
        assertThat(JsonReader.boolOr(m, "missing", true)).isTrue();
    }

    @Test
    void stringOrConvertsNonStringValues() {
        Map<String, Object> m = Map.of("a", 7, "b", true);
        assertThat(JsonReader.stringOr(m, "a", "")).isEqualTo("7");
        assertThat(JsonReader.stringOr(m, "b", "")).isEqualTo("true");
        assertThat(JsonReader.stringOr(m, "missing", "fallback")).isEqualTo("fallback");
    }

    @Test
    void objectOrReturnsDefaultWhenMissingOrWrongType() {
        Map<String, Object> m = Map.of("nested", Map.of("k", 1), "scalar", 7);
        assertThat(JsonReader.objectOr(m, "nested", Map.of()).get("k")).isEqualTo(1);
        assertThat(JsonReader.objectOr(m, "scalar", Map.of("d", 9))).containsEntry("d", 9);
        assertThat(JsonReader.objectOr(m, "missing", Map.of("d", 9))).containsEntry("d", 9);
    }

    @Test
    void stringListOrCoercesItems() {
        Map<String, Object> m = Map.of("xs", List.of("a", 1, true));
        assertThat(JsonReader.stringListOr(m, "xs", List.of()))
                .containsExactly("a", "1", "true");
        assertThat(JsonReader.stringListOr(m, "missing", List.of("d")))
                .containsExactly("d");
    }
}
