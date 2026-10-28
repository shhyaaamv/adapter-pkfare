package com.verteil.thirdparties.adapter.pkfare.acceptance.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The "shape" of a JSON document: every field path ({@code $.a.b[].c}, array elements merged under {@code []})
 * with its JSON type. Used to compare our PKFARE response with PKFARE's own sample, whose values come from a
 * different flight search and so can never match one to one.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonShape {

    public record Diff(List<String> missing, List<String> typeMismatches, List<String> extra) {
        public boolean matches() {
            return missing.isEmpty() && typeMismatches.isEmpty();
        }

        public String report() {
            return section("Missing in our response (present in the sample)", missing)
                    + section("Different JSON type than the sample", typeMismatches)
                    + section("Only in our response (not in the sample, informational)", extra);
        }

        private static String section(String title, List<String> lines) {
            return title + ": " + lines.size() + System.lineSeparator()
                    + lines.stream().map(line -> "  " + line + System.lineSeparator()).reduce("", String::concat)
                    + System.lineSeparator();
        }
    }

    public static Map<String, JsonNodeType> of(JsonNode root) {
        Map<String, JsonNodeType> shape = new TreeMap<>();
        walk(root, "$", shape);
        return shape;
    }

    /**
     * Sample paths whose only value is {@code null} are optional: the adapter's PKFARE mapper is NON_NULL, so such a
     * field is simply left out.
     */
    public static Diff compare(JsonNode sample, JsonNode actual) {
        Map<String, JsonNodeType> expected = of(sample);
        Map<String, JsonNodeType> found = of(actual);
        List<String> missing = expected.entrySet().stream()
                .filter(entry -> entry.getValue() != JsonNodeType.NULL && !found.containsKey(entry.getKey()))
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .toList();
        List<String> typeMismatches = expected.entrySet().stream()
                .filter(entry -> entry.getValue() != JsonNodeType.NULL && found.containsKey(entry.getKey())
                        && found.get(entry.getKey()) != JsonNodeType.NULL && found.get(entry.getKey()) != entry.getValue())
                .map(entry -> entry.getKey() + ": sample " + entry.getValue() + ", ours " + found.get(entry.getKey()))
                .toList();
        List<String> extra = found.entrySet().stream()
                .filter(entry -> !expected.containsKey(entry.getKey()))
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .toList();
        return new Diff(missing, typeMismatches, extra);
    }

    private static void walk(JsonNode node, String path, Map<String, JsonNodeType> shape) {
        // A path seen as null in one element and typed in another keeps the real type.
        shape.merge(path, node.getNodeType(), (existing, next) -> existing == JsonNodeType.NULL ? next : existing);
        if (node.isObject()) {
            node.properties().forEach(field -> walk(field.getValue(), path + "." + field.getKey(), shape));
        } else if (node.isArray()) {
            node.forEach(element -> walk(element, path + "[]", shape));
        }
    }
}
