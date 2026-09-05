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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Deserialises a Quint sum-type variant {@code { tag: "Name", value: … }} into the permitted
 * subtype of a sealed interface whose tag matches {@code Name}.
 *
 * <p>How {@code value} is mapped depends on the variant class:
 *
 * <ul>
 *   <li>a record with no components ignores {@code value} ({@code Empty} ↔ {@code Empty()});
 *   <li>if {@code value} is a record whose field names are exactly the component names, it maps
 *       field by field ({@code Move({ x: 1, y: 2 })} ↔ {@code Move(int x, int y)});
 *   <li>a record with one component receives the whole {@code value} ({@code Occupied(X)} ↔ {@code
 *       Occupied(Player player)});
 *   <li>a record with several components requires {@code value} to be a tuple with as many entries,
 *       mapped positionally ({@code Pair((1, "a"))} ↔ {@code Pair(int n, String s)});
 *   <li>any other class receives the whole {@code value}.
 * </ul>
 */
final class SealedTypeDeserializer extends StdDeserializer<Object> {

  private static final long serialVersionUID = 1L;

  private final JavaType type;
  private final transient SequencedMap<String, Class<?>> variants;

  SealedTypeDeserializer(JavaType type) {
    super(type);
    this.type = type;
    this.variants = Variants.variantsOf(type.getRawClass());
  }

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = ctxt.readTree(p);
    if (!ItfNodes.isVariantNode(node)) {
      throw JsonMappingException.from(
          p,
          "Cannot map "
              + describe(node)
              + " to sum type "
              + type.getRawClass().getName()
              + ": expected a variant record `{ tag, value }`");
    }
    String tag = node.get("tag").textValue();
    Class<?> variant = variants.get(tag);
    if (variant == null) {
      throw JsonMappingException.from(
          p,
          "Unknown variant `"
              + tag
              + "` for sum type "
              + type.getRawClass().getName()
              + "; expected one of "
              + variants.keySet());
    }
    JsonNode value = node.has("value") ? node.get("value") : JsonNodeFactory.instance.arrayNode();
    JavaType variantType = ctxt.constructType(variant);
    if (!variant.isRecord()) {
      return ctxt.readTreeAsValue(value, variantType);
    }
    RecordComponent[] components = variant.getRecordComponents();
    if (components.length == 0) {
      return ctxt.readTreeAsValue(JsonNodeFactory.instance.objectNode(), variantType);
    }
    if (value.isObject() && matchesComponentNames(value, components)) {
      return ctxt.readTreeAsValue(value, variantType);
    }
    if (components.length == 1) {
      ObjectNode wrapped = JsonNodeFactory.instance.objectNode();
      wrapped.set(Variants.propertyName(components[0]), value);
      return ctxt.readTreeAsValue(wrapped, variantType);
    }
    if (!value.isArray() || value.size() != components.length) {
      throw JsonMappingException.from(
          p,
          "Variant `"
              + tag
              + "` of "
              + type.getRawClass().getName()
              + " has "
              + components.length
              + " components but its value is "
              + describe(value)
              + "; expected a tuple with "
              + components.length
              + " entries");
    }
    return ctxt.readTreeAsValue(
        RecordTupleDeserializer.tupleToObject(value, components), variantType);
  }

  private static boolean matchesComponentNames(JsonNode value, RecordComponent[] components) {
    if (value.size() != components.length || ItfNodes.isVariantNode(value)) {
      return false;
    }
    Set<String> names = new HashSet<>();
    for (RecordComponent component : components) {
      names.add(Variants.propertyName(component));
    }
    for (Map.Entry<String, JsonNode> field : value.properties()) {
      if (!names.contains(field.getKey())) {
        return false;
      }
    }
    return true;
  }

  static String describe(JsonNode node) {
    return switch (node.getNodeType()) {
      case ARRAY -> "a tuple with " + node.size() + " entries";
      case OBJECT ->
          ItfNodes.isVariantNode(node)
              ? "variant `" + node.get("tag").textValue() + "`"
              : "a record " + node;
      default -> node.toString();
    };
  }
}
