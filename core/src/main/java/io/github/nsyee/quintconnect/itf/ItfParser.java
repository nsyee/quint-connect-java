/*
 * Copyright 2026 the quint-connect-java authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nsyee.quintconnect.itf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.SequencedMap;

/**
 * Parses <a href="https://apalache-mc.org/docs/adr/015adr-trace.html">ITF</a> JSON documents into
 * {@link ItfTrace} / {@link ItfValue} trees.
 *
 * <p>The parser is strict about the shape of the special {@code #…} objects and lenient about
 * everything else: unknown top-level or {@code #meta} fields are ignored, and a state without
 * {@code #meta.index} gets its position in the trace as index.
 */
public final class ItfParser {

  private static final String META = "#meta";
  private static final String BIGINT = "#bigint";
  private static final String TUP = "#tup";
  private static final String SET = "#set";
  private static final String MAP = "#map";
  private static final String UNSERIALIZABLE = "#unserializable";

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private ItfParser() {}

  /**
   * Parses the ITF trace stored at {@code path}.
   *
   * @throws ItfParseException if the file is not valid JSON or not a valid ITF trace
   * @throws UncheckedIOException if the file cannot be read
   */
  public static ItfTrace parse(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      return parse(in);
    } catch (ItfParseException e) {
      throw new ItfParseException("Failed to parse JSON trace file at: " + path, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Can't open trace file at: " + path, e);
    }
  }

  /**
   * Parses an ITF trace from a stream. The stream is not closed.
   *
   * @throws ItfParseException if the content is not valid JSON or not a valid ITF trace
   * @throws UncheckedIOException if the stream cannot be read
   */
  public static ItfTrace parse(InputStream in) {
    return parseTrace(readTree(in));
  }

  /**
   * Parses an ITF trace from a JSON string.
   *
   * @throws ItfParseException if the content is not valid JSON or not a valid ITF trace
   */
  public static ItfTrace parse(String json) {
    return parseTrace(readTree(json));
  }

  /**
   * Converts an already parsed JSON trace document.
   *
   * @throws ItfParseException if the document is not a valid ITF trace
   */
  public static ItfTrace parseTrace(JsonNode root) {
    if (!root.isObject()) {
      throw new ItfParseException("Expected a JSON object at the top level of an ITF trace");
    }
    ItfMeta meta = root.has(META) ? parseMeta(root.get(META)) : ItfMeta.EMPTY;
    List<String> vars = parseVars(root.get("vars"));
    List<ItfState> states = parseStates(root.get("states"));
    OptionalInt loop = parseLoop(root.get("loop"));
    return new ItfTrace(meta, vars, states, loop);
  }

  /**
   * Converts a JSON value into an {@link ItfValue}.
   *
   * @throws ItfParseException if a {@code #…} object is malformed
   */
  public static ItfValue parseValue(JsonNode node) {
    return switch (node.getNodeType()) {
      case BOOLEAN -> ItfValue.Bool.of(node.booleanValue());
      case STRING -> new ItfValue.Str(node.textValue());
      case NUMBER -> parseNumber(node);
      case ARRAY -> new ItfValue.List(parseElements(node));
      case OBJECT -> parseObject(node);
      case NULL, BINARY, MISSING, POJO ->
          throw new ItfParseException("Unsupported JSON node in ITF value: " + node.getNodeType());
    };
  }

  /**
   * Parses a single ITF value from a JSON string.
   *
   * @throws ItfParseException if the content is not valid JSON or a {@code #…} object is malformed
   */
  public static ItfValue parseValue(String json) {
    return parseValue(readTree(json));
  }

  private static JsonNode readTree(InputStream in) {
    try {
      return MAPPER.readTree(in);
    } catch (JsonProcessingException e) {
      throw new ItfParseException("Invalid JSON: " + e.getOriginalMessage(), e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode readTree(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new ItfParseException("Invalid JSON: " + e.getOriginalMessage(), e);
    }
  }

  private static ItfMeta parseMeta(JsonNode node) {
    if (!node.isObject()) {
      throw new ItfParseException("Expected `#meta` to be an object");
    }
    Map<String, String> varTypes = new LinkedHashMap<>();
    JsonNode types = node.get("varTypes");
    if (types != null && types.isObject()) {
      for (Map.Entry<String, JsonNode> entry : types.properties()) {
        if (entry.getValue().isTextual()) {
          varTypes.put(entry.getKey(), entry.getValue().textValue());
        }
      }
    }
    JsonNode timestamp = node.get("timestamp");
    return new ItfMeta(
        text(node, "format"),
        text(node, "format-description"),
        text(node, "source"),
        text(node, "status"),
        text(node, "description"),
        timestamp != null && timestamp.canConvertToLong()
            ? OptionalLong.of(timestamp.longValue())
            : OptionalLong.empty(),
        varTypes);
  }

  private static Optional<String> text(JsonNode object, String field) {
    JsonNode value = object.get(field);
    return value != null && value.isTextual() ? Optional.of(value.textValue()) : Optional.empty();
  }

  private static List<String> parseVars(JsonNode node) {
    if (node == null) {
      return List.of();
    }
    if (!node.isArray()) {
      throw new ItfParseException("Expected `vars` to be an array of strings");
    }
    List<String> vars = new ArrayList<>(node.size());
    for (JsonNode element : node) {
      if (!element.isTextual()) {
        throw new ItfParseException("Expected `vars` to be an array of strings");
      }
      vars.add(element.textValue());
    }
    return vars;
  }

  private static List<ItfState> parseStates(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw new ItfParseException("Expected `states` to be an array");
    }
    List<ItfState> states = new ArrayList<>(node.size());
    int position = 0;
    for (JsonNode element : node) {
      states.add(parseState(element, position));
      position++;
    }
    return states;
  }

  private static ItfState parseState(JsonNode node, int position) {
    if (!node.isObject()) {
      throw new ItfParseException("Expected state " + position + " to be an object");
    }
    int index = position;
    JsonNode meta = node.get(META);
    if (meta != null && meta.isObject()) {
      JsonNode idx = meta.get("index");
      if (idx != null && idx.canConvertToInt()) {
        index = idx.intValue();
      }
    }
    SequencedMap<String, ItfValue> fields = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : node.properties()) {
      if (META.equals(entry.getKey())) {
        continue;
      }
      fields.put(entry.getKey(), parseValue(entry.getValue()));
    }
    return new ItfState(index, new ItfValue.Record(fields));
  }

  private static OptionalInt parseLoop(JsonNode node) {
    if (node == null || node.isNull()) {
      return OptionalInt.empty();
    }
    if (!node.canConvertToInt()) {
      throw new ItfParseException("Expected `loop` to be an integer");
    }
    return OptionalInt.of(node.intValue());
  }

  private static ItfValue parseNumber(JsonNode node) {
    if (!node.isIntegralNumber()) {
      throw new ItfParseException("Non-integer numbers are not valid ITF values: " + node);
    }
    return new ItfValue.Int(node.bigIntegerValue());
  }

  private static ItfValue parseObject(JsonNode node) {
    if (node.size() == 1) {
      String key = node.properties().iterator().next().getKey();
      JsonNode inner = node.get(key);
      switch (key) {
        case BIGINT -> {
          return parseBigInt(inner);
        }
        case TUP -> {
          return new ItfValue.Tuple(parseElements(expectArray(TUP, inner)));
        }
        case SET -> {
          return new ItfValue.Set(parseElements(expectArray(SET, inner)));
        }
        case MAP -> {
          return parseMap(expectArray(MAP, inner));
        }
        case UNSERIALIZABLE -> {
          if (!inner.isTextual()) {
            throw new ItfParseException("Expected `#unserializable` to hold a string");
          }
          return new ItfValue.Unserializable(inner.textValue());
        }
        default -> {
          // fall through to the record case
        }
      }
    }
    SequencedMap<String, ItfValue> fields = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : node.properties()) {
      fields.put(entry.getKey(), parseValue(entry.getValue()));
    }
    return new ItfValue.Record(fields);
  }

  private static ItfValue parseBigInt(JsonNode inner) {
    if (inner.isTextual()) {
      try {
        return new ItfValue.Int(new BigInteger(inner.textValue()));
      } catch (NumberFormatException e) {
        throw new ItfParseException("Invalid `#bigint` literal: " + inner.textValue(), e);
      }
    }
    if (inner.isIntegralNumber()) {
      return new ItfValue.Int(inner.bigIntegerValue());
    }
    throw new ItfParseException("Expected `#bigint` to hold a decimal string");
  }

  private static JsonNode expectArray(String key, JsonNode inner) {
    if (!inner.isArray()) {
      throw new ItfParseException("Expected `" + key + "` to hold an array");
    }
    return inner;
  }

  private static List<ItfValue> parseElements(JsonNode array) {
    List<ItfValue> items = new ArrayList<>(array.size());
    for (JsonNode element : array) {
      items.add(parseValue(element));
    }
    return items;
  }

  private static ItfValue parseMap(JsonNode array) {
    List<ItfValue.Map.Entry> entries = new ArrayList<>(array.size());
    for (JsonNode pair : array) {
      if (!pair.isArray() || pair.size() != 2) {
        throw new ItfParseException("Expected `#map` entries to be [key, value] pairs");
      }
      entries.add(new ItfValue.Map.Entry(parseValue(pair.get(0)), parseValue(pair.get(1))));
    }
    return new ItfValue.Map(entries);
  }
}
