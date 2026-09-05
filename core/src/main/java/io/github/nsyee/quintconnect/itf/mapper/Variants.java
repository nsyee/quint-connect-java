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
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

/** Reflection helpers shared by the sum-type deserializer and the reverse mapping. */
final class Variants {

  private Variants() {}

  /**
   * Whether {@code type} is a sealed interface or abstract class whose variants we can enumerate.
   */
  static boolean isSealedRoot(Class<?> type) {
    return type.isSealed()
        && (type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers()));
  }

  /** The Quint tag of a variant class: {@link ItfVariant#value()} or the simple class name. */
  static String tagOf(Class<?> variant) {
    ItfVariant annotation = variant.getAnnotation(ItfVariant.class);
    return annotation != null && !annotation.value().isEmpty()
        ? annotation.value()
        : variant.getSimpleName();
  }

  /**
   * Whether a one-component variant's payload is written as a record, see {@link
   * ItfVariant#record}.
   */
  static boolean hasRecordPayload(Class<?> variant) {
    ItfVariant annotation = variant.getAnnotation(ItfVariant.class);
    return annotation != null && annotation.record();
  }

  /**
   * All concrete variants reachable from {@code root}, keyed by tag. Nested sealed interfaces are
   * flattened, so {@code sealed interface A permits B, C; sealed interface B permits D} yields
   * {@code {D, C}}.
   */
  static SequencedMap<String, Class<?>> variantsOf(Class<?> root) {
    SequencedMap<String, Class<?>> variants = new LinkedHashMap<>();
    collect(root, variants);
    return variants;
  }

  private static void collect(Class<?> type, Map<String, Class<?>> variants) {
    for (Class<?> permitted : type.getPermittedSubclasses()) {
      if (isSealedRoot(permitted)) {
        collect(permitted, variants);
      } else {
        String tag = tagOf(permitted);
        Class<?> previous = variants.putIfAbsent(tag, permitted);
        if (previous != null) {
          throw new IllegalArgumentException(
              "Variants "
                  + previous.getName()
                  + " and "
                  + permitted.getName()
                  + " both map to tag `"
                  + tag
                  + "`");
        }
      }
    }
  }

  /** Whether {@code type} is a record that implements a sealed interface (i.e. a variant). */
  static boolean isVariantRecord(Class<?> type) {
    if (!type.isRecord()) {
      return false;
    }
    for (Class<?> iface : type.getInterfaces()) {
      if (iface.isSealed()) {
        return true;
      }
    }
    Class<?> parent = type.getSuperclass();
    return parent != null && parent.isSealed() && parent != Record.class;
  }

  /** The JSON property name of a record component: {@code @JsonProperty} or the component name. */
  static String propertyName(RecordComponent component) {
    // @JsonProperty does not target RECORD_COMPONENT, so javac propagates it to the accessor.
    JsonProperty annotation = component.getAccessor().getAnnotation(JsonProperty.class);
    if (annotation == null) {
      annotation = component.getAnnotation(JsonProperty.class);
    }
    if (annotation != null && !annotation.value().isEmpty()) {
      return annotation.value();
    }
    return component.getName();
  }
}
