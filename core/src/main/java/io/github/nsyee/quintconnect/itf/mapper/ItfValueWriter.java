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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nsyee.quintconnect.itf.ItfParser;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;

/**
 * The reverse mapping, Java object → {@link ItfValue}, used to render the implementation state in
 * the same canonical form as the specification state. Sets and maps are emitted sorted by the
 * display form of their elements / keys so that two equal values always render identically.
 */
final class ItfValueWriter {

  private static final Comparator<ItfValue> BY_DISPLAY = Comparator.comparing(ItfValue::display);

  private final ObjectMapper fallback;

  ItfValueWriter(ObjectMapper fallback) {
    this.fallback = fallback;
  }

  ItfValue write(Object value) {
    return switch (value) {
      case null -> throw new ItfMappingException("Cannot map null to an ITF value; use Optional");
      case ItfValue itf -> itf;
      case Boolean b -> ItfValue.Bool.of(b);
      case BigInteger i -> new ItfValue.Int(i);
      case Integer i -> ItfValue.Int.of(i);
      case Long l -> ItfValue.Int.of(l);
      case Short s -> ItfValue.Int.of(s);
      case Byte b -> ItfValue.Int.of(b);
      case Number n ->
          throw new ItfMappingException(
              "Cannot map " + n.getClass().getName() + " to an ITF value: Quint has no floats");
      case String s -> new ItfValue.Str(s);
      case Character c -> new ItfValue.Str(c.toString());
      case Enum<?> e -> variant(enumTag(e), ItfValue.Tuple.of());
      case Optional<?> o ->
          o.map(inner -> variant("Some", write(inner)))
              .orElseGet(() -> variant("None", ItfValue.Tuple.of()));
      case Set<?> set -> set(set);
      case Map<?, ?> map -> map(map);
      case Collection<?> items -> list(items);
      case Record r -> record(r);
      default -> value.getClass().isArray() ? array(value) : pojo(value);
    };
  }

  private static ItfValue variant(String tag, ItfValue value) {
    return ItfValue.Record.of("tag", new ItfValue.Str(tag), "value", value);
  }

  private static String enumTag(Enum<?> constant) {
    try {
      JsonProperty annotation =
          constant.getDeclaringClass().getField(constant.name()).getAnnotation(JsonProperty.class);
      if (annotation != null && !annotation.value().isEmpty()) {
        return annotation.value();
      }
    } catch (NoSuchFieldException e) {
      // enum constants always have a matching field; fall through to the name
    }
    return constant.name();
  }

  private ItfValue set(Set<?> set) {
    List<ItfValue> items = new ArrayList<>(set.size());
    for (Object item : set) {
      items.add(write(item));
    }
    items.sort(BY_DISPLAY);
    return new ItfValue.Set(items);
  }

  private ItfValue map(Map<?, ?> map) {
    List<ItfValue.Map.Entry> entries = new ArrayList<>(map.size());
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      entries.add(new ItfValue.Map.Entry(write(entry.getKey()), write(entry.getValue())));
    }
    entries.sort(Comparator.comparing(ItfValue.Map.Entry::key, BY_DISPLAY));
    return new ItfValue.Map(entries);
  }

  private ItfValue list(Collection<?> items) {
    List<ItfValue> values = new ArrayList<>(items.size());
    for (Object item : items) {
      values.add(write(item));
    }
    return new ItfValue.List(values);
  }

  private ItfValue array(Object array) {
    int length = Array.getLength(array);
    List<ItfValue> values = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      values.add(write(Array.get(array, i)));
    }
    return new ItfValue.List(values);
  }

  /**
   * A record maps to an ITF record field by field. A record that is a variant of a sealed interface
   * becomes {@code { tag, value }}: no components → unit, one component → that component, several
   * components → a record of them.
   */
  private ItfValue record(Record record) {
    Class<?> type = record.getClass();
    RecordComponent[] components = type.getRecordComponents();
    if (!Variants.isVariantRecord(type)) {
      return fields(record, components);
    }
    String tag = Variants.tagOf(type);
    return switch (components.length) {
      case 0 -> variant(tag, ItfValue.Tuple.of());
      case 1 ->
          Variants.hasRecordPayload(type)
              ? variant(tag, fields(record, components))
              : variant(tag, write(component(record, components[0])));
      default -> variant(tag, fields(record, components));
    };
  }

  private ItfValue fields(Record record, RecordComponent[] components) {
    SequencedMap<String, ItfValue> fields = new LinkedHashMap<>();
    for (RecordComponent component : components) {
      fields.put(Variants.propertyName(component), write(component(record, component)));
    }
    return new ItfValue.Record(fields);
  }

  private static Object component(Record record, RecordComponent component) {
    try {
      var accessor = component.getAccessor();
      accessor.setAccessible(true);
      return accessor.invoke(record);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new ItfMappingException(
          "Cannot read component `" + component.getName() + "` of " + record.getClass().getName(),
          e);
    }
  }

  /** Anything else goes through Jackson's regular bean serialisation and the ITF parser. */
  private ItfValue pojo(Object value) {
    JsonNode tree = fallback.valueToTree(value);
    return ItfParser.parseValue(tree);
  }
}
