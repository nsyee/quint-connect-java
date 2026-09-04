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

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * A value in an <a href="https://apalache-mc.org/docs/adr/015adr-trace.html">Informal Trace
 * Format</a> (ITF) trace.
 *
 * <p>The hierarchy mirrors the JSON encoding used by Quint and Apalache:
 *
 * <ul>
 *   <li>JSON booleans, strings and numbers become {@link Bool}, {@link Str} and {@link Int};
 *   <li>{@code {"#bigint": "…"}} becomes {@link Int};
 *   <li>JSON arrays become {@link List}, {@code {"#tup": […]}} becomes {@link Tuple}, {@code
 *       {"#set": […]}} becomes {@link Set} and {@code {"#map": [[k, v], …]}} becomes {@link Map};
 *   <li>{@code {"#unserializable": "…"}} becomes {@link Unserializable};
 *   <li>any other JSON object becomes {@link Record}. Quint sum-type variants are records of the
 *       form {@code { tag: "Name", value: … }}.
 * </ul>
 *
 * <p>Equality is structural. {@link Set} and {@link Map} compare regardless of element order; every
 * other collection is ordered. All values are immutable.
 */
public sealed interface ItfValue
    permits ItfValue.Bool,
        ItfValue.Int,
        ItfValue.Str,
        ItfValue.List,
        ItfValue.Tuple,
        ItfValue.Set,
        ItfValue.Map,
        ItfValue.Record,
        ItfValue.Unserializable {

  /** Renders this value in Quint-like syntax; see {@link ItfDisplay}. */
  default String display() {
    return ItfDisplay.format(this);
  }

  /**
   * A boolean.
   *
   * @param value the boolean value
   */
  record Bool(boolean value) implements ItfValue {
    /** The {@code true} constant. */
    public static final Bool TRUE = new Bool(true);

    /** The {@code false} constant. */
    public static final Bool FALSE = new Bool(false);

    /** Returns the shared instance for {@code value}. */
    public static Bool of(boolean value) {
      return value ? TRUE : FALSE;
    }
  }

  /**
   * An integer. Both plain JSON numbers and {@code {"#bigint": "…"}} objects map to this type
   * because Quint integers are unbounded.
   *
   * @param value the integer value
   */
  record Int(BigInteger value) implements ItfValue {
    /** Creates an integer value, rejecting {@code null}. */
    public Int {
      Objects.requireNonNull(value, "value");
    }

    /** Creates an integer value from a {@code long}. */
    public static Int of(long value) {
      return new Int(BigInteger.valueOf(value));
    }

    /** Creates an integer value from its decimal representation. */
    public static Int of(String decimal) {
      return new Int(new BigInteger(decimal));
    }
  }

  /**
   * A string.
   *
   * @param value the string value
   */
  record Str(String value) implements ItfValue {
    /** Creates a string value, rejecting {@code null}. */
    public Str {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * An ordered list (JSON array).
   *
   * @param items the elements, in order
   */
  record List(java.util.List<ItfValue> items) implements ItfValue {
    /** Creates a list, taking an immutable copy of {@code items}. */
    public List {
      items = java.util.List.copyOf(items);
    }

    /** Creates a list from the given elements. */
    public static List of(ItfValue... items) {
      return new List(java.util.List.of(items));
    }
  }

  /**
   * A tuple ({@code {"#tup": […]}}).
   *
   * @param items the components, in order
   */
  record Tuple(java.util.List<ItfValue> items) implements ItfValue {
    /** Creates a tuple, taking an immutable copy of {@code items}. */
    public Tuple {
      items = java.util.List.copyOf(items);
    }

    /** Creates a tuple from the given components. */
    public static Tuple of(ItfValue... items) {
      return new Tuple(java.util.List.of(items));
    }

    /** Returns whether this is the unit tuple {@code ()}. */
    public boolean isEmpty() {
      return items.isEmpty();
    }
  }

  /**
   * A set ({@code {"#set": […]}}). The original element order is preserved for display, but {@link
   * #equals} and {@link #hashCode} ignore order and duplicates.
   *
   * @param items the elements, in the order they were written
   */
  record Set(java.util.List<ItfValue> items) implements ItfValue {
    /** Creates a set, taking an immutable copy of {@code items}. */
    public Set {
      items = java.util.List.copyOf(items);
    }

    /** Creates a set from the given elements. */
    public static Set of(ItfValue... items) {
      return new Set(java.util.List.of(items));
    }

    /** Returns the elements as an unordered, duplicate-free {@link java.util.Set}. */
    public java.util.Set<ItfValue> toSet() {
      return Collections.unmodifiableSet(new HashSet<>(items));
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Set that && toSet().equals(that.toSet());
    }

    @Override
    public int hashCode() {
      return toSet().hashCode();
    }
  }

  /**
   * A map ({@code {"#map": [[k, v], …]}}) whose keys may be arbitrary values. The original entry
   * order is preserved for display, but {@link #equals} and {@link #hashCode} ignore order.
   *
   * @param entries the entries, in the order they were written
   */
  record Map(java.util.List<Entry> entries) implements ItfValue {
    /** Creates a map, taking an immutable copy of {@code entries}. */
    public Map {
      entries = java.util.List.copyOf(entries);
    }

    /** Creates a map from the given entries. */
    public static Map of(Entry... entries) {
      return new Map(java.util.List.of(entries));
    }

    /** Returns the entries as an unordered {@link java.util.Map}; later duplicates win. */
    public java.util.Map<ItfValue, ItfValue> toMap() {
      java.util.Map<ItfValue, ItfValue> map = new LinkedHashMap<>();
      for (Entry entry : entries) {
        map.put(entry.key(), entry.value());
      }
      return Collections.unmodifiableMap(map);
    }

    /** Looks up {@code key}, returning the first matching entry's value. */
    public Optional<ItfValue> get(ItfValue key) {
      for (Entry entry : entries) {
        if (entry.key().equals(key)) {
          return Optional.of(entry.value());
        }
      }
      return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Map that && toMap().equals(that.toMap());
    }

    @Override
    public int hashCode() {
      return toMap().hashCode();
    }

    /**
     * A key/value pair of a {@link Map}.
     *
     * @param key the key
     * @param value the value
     */
    public record Entry(ItfValue key, ItfValue value) {
      /** Creates an entry, rejecting {@code null}. */
      public Entry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
      }
    }
  }

  /**
   * A record (plain JSON object). Field order is preserved for display; equality follows {@link
   * java.util.Map#equals}, i.e. it ignores order.
   *
   * @param fields the fields, in the order they were written
   */
  @SuppressWarnings("AvoidCommonTypeNames") // always referenced as ItfValue.Record
  record Record(SequencedMap<String, ItfValue> fields) implements ItfValue {
    /** Creates a record, taking an immutable copy of {@code fields}. */
    public Record {
      fields = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(fields));
    }

    /** Creates a record from alternating field names and values. */
    public static Record of(Object... namesAndValues) {
      if (namesAndValues.length % 2 != 0) {
        throw new IllegalArgumentException("Expected an even number of arguments");
      }
      SequencedMap<String, ItfValue> fields = new LinkedHashMap<>();
      for (int i = 0; i < namesAndValues.length; i += 2) {
        fields.put((String) namesAndValues[i], (ItfValue) namesAndValues[i + 1]);
      }
      return new Record(fields);
    }

    /** Returns the value of field {@code name}, if present. */
    public Optional<ItfValue> get(String name) {
      return Optional.ofNullable(fields.get(name));
    }

    /** Returns whether the record has a field called {@code name}. */
    public boolean has(String name) {
      return fields.containsKey(name);
    }

    /** Returns the number of fields. */
    public int size() {
      return fields.size();
    }

    /** Returns a copy of this record without field {@code name}. */
    public Record without(String name) {
      if (!fields.containsKey(name)) {
        return this;
      }
      SequencedMap<String, ItfValue> copy = new LinkedHashMap<>(fields);
      copy.remove(name);
      return new Record(copy);
    }

    /**
     * Returns the tag of this record if it is a sum-type variant, i.e. it has exactly the two
     * fields {@code tag} (a string) and {@code value}.
     */
    public Optional<String> variantTag() {
      if (fields.size() == 2
          && fields.get("tag") instanceof Str(String tag)
          && fields.containsKey("value")) {
        return Optional.of(tag);
      }
      return Optional.empty();
    }
  }

  /**
   * A value Quint could not serialise, e.g. an infinite set ({@code {"#unserializable": "…"}}).
   *
   * @param repr the textual representation emitted by the tool
   */
  record Unserializable(String repr) implements ItfValue {
    /** Creates an unserializable marker, rejecting {@code null}. */
    public Unserializable {
      Objects.requireNonNull(repr, "repr");
    }
  }
}
