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
package io.github.nsyee.quintconnect.itf.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.util.Map;

/**
 * Converts {@link ItfValue} trees into the intermediate {@link JsonNode} form consumed by the
 * deserializers of {@link ItfModule}.
 *
 * <p>The encoding is deliberately simpler than ITF JSON: lists, tuples and sets all become arrays,
 * integers become plain numbers and maps whose keys are all strings become objects. Only maps with
 * other keys keep the {@code {"#map": [[k, v], …]}} shape, and unserializable values keep {@code
 * {"#unserializable": "…"}} so that mapping them fails with a clear message.
 */
final class ItfNodes {

  static final String MAP = "#map";
  static final String UNSERIALIZABLE = "#unserializable";

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private ItfNodes() {}

  static JsonNode toJsonNode(ItfValue value) {
    return switch (value) {
      case ItfValue.Bool(boolean b) -> NODES.booleanNode(b);
      case ItfValue.Int(var i) -> NODES.numberNode(i);
      case ItfValue.Str(String s) -> NODES.textNode(s);
      case ItfValue.List(var items) -> array(items);
      case ItfValue.Tuple(var items) -> array(items);
      case ItfValue.Set(var items) -> array(items);
      case ItfValue.Map map -> map(map);
      case ItfValue.Record(var fields) -> {
        ObjectNode object = NODES.objectNode();
        for (Map.Entry<String, ItfValue> field : fields.entrySet()) {
          object.set(field.getKey(), toJsonNode(field.getValue()));
        }
        yield object;
      }
      case ItfValue.Unserializable(String repr) -> NODES.objectNode().put(UNSERIALIZABLE, repr);
    };
  }

  /** Whether {@code node} is the {@code {"#map": […]}} encoding of a map with non-string keys. */
  static boolean isMapNode(JsonNode node) {
    return node.isObject() && node.size() == 1 && node.has(MAP) && node.get(MAP).isArray();
  }

  /**
   * Whether {@code node} is a Quint sum-type variant, i.e. an object with a textual {@code tag}.
   */
  static boolean isVariantNode(JsonNode node) {
    return node.isObject()
        && node.size() <= 2
        && node.has("tag")
        && node.get("tag").isTextual()
        && (node.size() == 1 || node.has("value"));
  }

  private static ArrayNode array(java.util.List<ItfValue> items) {
    ArrayNode array = NODES.arrayNode(items.size());
    for (ItfValue item : items) {
      array.add(toJsonNode(item));
    }
    return array;
  }

  private static JsonNode map(ItfValue.Map map) {
    boolean stringKeys = true;
    for (ItfValue.Map.Entry entry : map.entries()) {
      if (!(entry.key() instanceof ItfValue.Str)) {
        stringKeys = false;
        break;
      }
    }
    if (stringKeys) {
      ObjectNode object = NODES.objectNode();
      for (ItfValue.Map.Entry entry : map.entries()) {
        object.set(((ItfValue.Str) entry.key()).value(), toJsonNode(entry.value()));
      }
      return object;
    }
    ArrayNode pairs = NODES.arrayNode(map.entries().size());
    for (ItfValue.Map.Entry entry : map.entries()) {
      pairs.add(NODES.arrayNode(2).add(toJsonNode(entry.key())).add(toJsonNode(entry.value())));
    }
    return NODES.objectNode().set(MAP, pairs);
  }
}
