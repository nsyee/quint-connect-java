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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Converts {@link ItfValue} trees into user-defined Java types and back.
 *
 * <p>This is the Java counterpart of {@code serde::Deserialize} in the Rust crate: drivers declare
 * the specification state and nondet picks as plain records, enums and sealed interfaces, and the
 * mapper fills them from the trace. The rules are listed on {@link ItfModule}; briefly:
 *
 * <pre>{@code
 * record GameState(Map<Integer, Map<Integer, Square>> board, Player nextTurn) {}
 * sealed interface Square permits Occupied, Empty {}
 * record Occupied(Player player) implements Square {}
 * record Empty() implements Square {}
 * enum Player { X, O }
 *
 * ItfMapper mapper = new ItfMapper();
 * GameState state = mapper.fromItf(step.specState(), GameState.class);
 * Position pos = step.pick("coordinate", mapper.to(Position.class));
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ItfMapper {

  private static final ItfMapper DEFAULT = new ItfMapper();

  private final ObjectMapper mapper;
  private final ItfValueWriter writer;

  /**
   * Creates a mapper with the default configuration: unknown record fields are ignored, missing
   * ones and cross-type coercions (e.g. {@code 1} → {@code boolean}, {@code "1"} → {@code int}) are
   * errors.
   */
  public ItfMapper() {
    this(
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .withCoercionConfigDefaults(
                cfg ->
                    cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail))
            .build());
  }

  /**
   * Creates a mapper on top of a user-configured {@link ObjectMapper}. A copy of {@code base} with
   * {@link ItfModule} registered is used, so the argument is not modified.
   */
  public ItfMapper(ObjectMapper base) {
    this.mapper = base.copy().registerModule(new ItfModule());
    this.writer = new ItfValueWriter(mapper);
  }

  /** The shared default instance. */
  public static ItfMapper defaultMapper() {
    return DEFAULT;
  }

  /**
   * Maps {@code value} to an instance of {@code type}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  public <T> T fromItf(ItfValue value, Class<T> type) {
    return cast(type, fromItf(value, mapper.constructType(type)));
  }

  /**
   * Maps {@code value} to an instance of the generic type described by {@code type}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  @SuppressWarnings("unchecked")
  public <T> T fromItf(ItfValue value, TypeReference<T> type) {
    return (T) fromItf(value, mapper.constructType(type));
  }

  /**
   * Maps {@code value} to an instance of the Jackson {@code type}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  public Object fromItf(ItfValue value, JavaType type) {
    Objects.requireNonNull(value, "value");
    JsonNode node = ItfNodes.toJsonNode(value);
    try {
      return mapper.readerFor(type).readValue(node);
    } catch (IOException | IllegalArgumentException e) {
      throw new ItfMappingException(
          "Cannot map " + value.display() + " to " + type.toCanonical() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Maps a specification state to {@code type}, wrapping failures with the upstream hint {@link
   * ItfMappingException#STATE_HINT}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  public <T> T stateFromItf(ItfValue state, Class<T> type) {
    return cast(type, stateFromItf(state, mapper.constructType(type)));
  }

  /**
   * Maps a specification state to the generic type described by {@code type}, wrapping failures
   * with the upstream hint {@link ItfMappingException#STATE_HINT}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  @SuppressWarnings("unchecked")
  public <T> T stateFromItf(ItfValue state, TypeReference<T> type) {
    return (T) stateFromItf(state, mapper.constructType(type));
  }

  /**
   * Maps a specification state to the Jackson {@code type}, wrapping failures with the upstream
   * hint {@link ItfMappingException#STATE_HINT}.
   *
   * @throws ItfMappingException if the value does not fit the type
   */
  public Object stateFromItf(ItfValue state, JavaType type) {
    try {
      return fromItf(state, type);
    } catch (ItfMappingException e) {
      throw ItfMappingException.forState(e);
    }
  }

  /** A converter for {@code Step.pick(name, converter)} that maps picks to {@code type}. */
  public <T> Function<ItfValue, T> to(Class<T> type) {
    JavaType javaType = mapper.constructType(type);
    return value -> cast(type, fromItf(value, javaType));
  }

  /** A converter for {@code Step.pick(name, converter)} that maps picks to a generic type. */
  @SuppressWarnings("unchecked")
  public <T> Function<ItfValue, T> to(TypeReference<T> type) {
    JavaType javaType = mapper.constructType(type);
    return value -> (T) fromItf(value, javaType);
  }

  /**
   * Maps a Java value to its canonical {@link ItfValue}: records and POJOs become records, variants
   * of sealed interfaces and enums become {@code { tag, value }}, {@link java.util.Optional}
   * becomes {@code Some}/{@code None}, sets and maps are emitted sorted by display form.
   *
   * @throws ItfMappingException if the value has no ITF representation (e.g. {@code null}, floats)
   */
  public ItfValue toItf(Object value) {
    return writer.write(value);
  }

  /** Like {@link Class#cast} but tolerant of primitive types, whose values arrive boxed. */
  @SuppressWarnings("unchecked")
  private static <T> T cast(Class<T> type, Object value) {
    return type.isPrimitive() ? (T) value : type.cast(value);
  }
}
