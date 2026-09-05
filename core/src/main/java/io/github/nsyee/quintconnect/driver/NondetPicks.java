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
package io.github.nsyee.quintconnect.driver;

import io.github.nsyee.quintconnect.itf.ItfValue;
import io.github.nsyee.quintconnect.itf.ItfValues;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;

/**
 * The nondeterministic choices Quint made while producing one step of a trace.
 *
 * <p>Quint records every {@code nondet} variable of the specification as an {@code Option}: {@code
 * Some(v)} when the variable was picked for the action taken and {@code None} otherwise. This class
 * unwraps those options, so that {@link #get} only returns the variables that were actually picked.
 */
public final class NondetPicks implements Iterable<Map.Entry<String, ItfValue>> {

  private static final NondetPicks EMPTY = new NondetPicks(new LinkedHashMap<>());

  private final SequencedMap<String, ItfValue> picks;

  private NondetPicks(SequencedMap<String, ItfValue> picks) {
    this.picks = Collections.unmodifiableSequencedMap(picks);
  }

  /** Picks with no entries. */
  public static NondetPicks empty() {
    return EMPTY;
  }

  /**
   * Builds picks from a record, unwrapping each field with {@link ItfValues#asOption} and dropping
   * the ones that are {@code None}.
   */
  public static NondetPicks of(ItfValue.Record record) {
    SequencedMap<String, ItfValue> picks = new LinkedHashMap<>();
    for (Map.Entry<String, ItfValue> field : record.fields().entrySet()) {
      ItfValues.asOption(field.getValue()).ifPresent(value -> picks.put(field.getKey(), value));
    }
    return new NondetPicks(picks);
  }

  /**
   * Builds picks from an arbitrary value, which must be a record.
   *
   * @throws DriverException if {@code value} is not a record
   */
  public static NondetPicks of(ItfValue value) {
    if (value instanceof ItfValue.Record record) {
      return of(record);
    }
    throw new DriverException("Expected nondet picks to be a `Value::Record`");
  }

  /** Returns the value picked for {@code name}, if that variable was picked in this step. */
  public Optional<ItfValue> get(String name) {
    return Optional.ofNullable(picks.get(name));
  }

  /** Whether {@code name} was picked in this step. */
  public boolean contains(String name) {
    return picks.containsKey(name);
  }

  /** Names of the picked variables, in trace order. */
  public Set<String> names() {
    return picks.keySet();
  }

  /** The picks as an immutable map, in trace order. */
  public SequencedMap<String, ItfValue> asMap() {
    return picks;
  }

  /** Whether nothing was picked. */
  public boolean isEmpty() {
    return picks.isEmpty();
  }

  /** Number of picked variables. */
  public int size() {
    return picks.size();
  }

  @Override
  public Iterator<Map.Entry<String, ItfValue>> iterator() {
    return picks.entrySet().iterator();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NondetPicks that && picks.equals(that.picks);
  }

  @Override
  public int hashCode() {
    return picks.hashCode();
  }

  /** One {@code + name: value} line per pick, as printed by the Rust crate. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, ItfValue> pick : picks.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append('\n');
      }
      sb.append("+ ").append(pick.getKey()).append(": ").append(pick.getValue().display());
    }
    return sb.toString();
  }
}
