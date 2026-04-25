package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

    @Test
    void primitives() {
        assertThat(Json.render(null)).isEqualTo("null");
        assertThat(Json.render(true)).isEqualTo("true");
        assertThat(Json.render(42)).isEqualTo("42");
        assertThat(Json.render("hi")).isEqualTo("\"hi\"");
    }

    @Test
    void escapesControlAndSpecialChars() {
        assertThat(Json.render("a\"b\\c\n\t")).isEqualTo("\"a\\\"b\\\\c\\n\\t\"");
        assertThat(Json.render("")).isEqualTo("\"\\u0001\"");
    }

    @Test
    void unicodePassesThrough() {
        assertThat(Json.render("äö")).isEqualTo("\"äö\"");
    }

    @Test
    void objectKeysPreserveInsertionOrder() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 1);
        m.put("a", 2);
        assertThat(Json.render(m)).isEqualTo("{\"b\":1,\"a\":2}");
    }

    @Test
    void arrays() {
        assertThat(Json.render(List.of(1, 2, 3))).isEqualTo("[1,2,3]");
        assertThat(Json.render(List.of())).isEqualTo("[]");
    }

    @Test
    void nested() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("xs", List.of(1, Map.of("k", "v")));
        assertThat(Json.render(root)).isEqualTo("{\"xs\":[1,{\"k\":\"v\"}]}");
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> Json.render(new Object()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
