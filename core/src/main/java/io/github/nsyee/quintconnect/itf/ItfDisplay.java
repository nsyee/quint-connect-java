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

import java.util.Iterator;
import java.util.Map;

/**
 * Renders {@link ItfValue} trees in Quint-like syntax, e.g. {@code Set(1, 2)}, {@code Map(1 ->
 * "a")}, {@code Occupied(X)} or <code>{ a: 1, b: true }</code>.
 *
 * <p>Sum-type variants (records with exactly the fields {@code tag} and {@code value}) are shown as
 * {@code Tag}, {@code Tag(v)} or {@code Tag(v1, v2)}; the unit tuple is omitted.
 */
public final class ItfDisplay {

  private ItfDisplay() {}

  /** Formats {@code value}. */
  public static String format(ItfValue value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value);
    return sb.toString();
  }

  private static void write(StringBuilder sb, ItfValue value) {
    switch (value) {
      case ItfValue.Bool(boolean b) -> sb.append(b);
      case ItfValue.Int(var n) -> sb.append(n);
      case ItfValue.Str(String s) -> sb.append('"').append(s).append('"');
      case ItfValue.List(var items) -> writeCollection(sb, "List(", items, ")");
      case ItfValue.Tuple(var items) -> writeCollection(sb, "(", items, ")");
      case ItfValue.Set(var items) -> writeCollection(sb, "Set(", items, ")");
      case ItfValue.Map map -> writeMap(sb, map);
      case ItfValue.Record rec -> writeRecord(sb, rec);
      case ItfValue.Unserializable(String repr) -> writeDebugString(sb, repr);
    }
  }

  private static void writeCollection(
      StringBuilder sb, String open, Iterable<ItfValue> items, String close) {
    sb.append(open);
    writeElements(sb, items);
    sb.append(close);
  }

  private static void writeElements(StringBuilder sb, Iterable<ItfValue> items) {
    Iterator<ItfValue> it = items.iterator();
    if (it.hasNext()) {
      write(sb, it.next());
      while (it.hasNext()) {
        sb.append(", ");
        write(sb, it.next());
      }
    }
  }

  private static void writeMap(StringBuilder sb, ItfValue.Map map) {
    sb.append("Map(");
    Iterator<ItfValue.Map.Entry> it = map.entries().iterator();
    if (it.hasNext()) {
      writeEntry(sb, it.next());
      while (it.hasNext()) {
        sb.append(", ");
        writeEntry(sb, it.next());
      }
    }
    sb.append(')');
  }

  private static void writeEntry(StringBuilder sb, ItfValue.Map.Entry entry) {
    write(sb, entry.key());
    sb.append(" -> ");
    write(sb, entry.value());
  }

  private static void writeRecord(StringBuilder sb, ItfValue.Record rec) {
    var tag = rec.variantTag();
    if (tag.isPresent()) {
      sb.append(tag.get());
      ItfValue payload = rec.fields().get("value");
      if (payload instanceof ItfValue.Tuple tuple) {
        if (!tuple.isEmpty()) {
          write(sb, tuple);
        }
      } else {
        sb.append('(');
        write(sb, payload);
        sb.append(')');
      }
      return;
    }
    sb.append("{ ");
    Iterator<Map.Entry<String, ItfValue>> it = rec.fields().entrySet().iterator();
    if (it.hasNext()) {
      writeField(sb, it.next());
      while (it.hasNext()) {
        sb.append(", ");
        writeField(sb, it.next());
      }
    }
    sb.append(" }");
  }

  private static void writeField(StringBuilder sb, Map.Entry<String, ItfValue> field) {
    sb.append(field.getKey()).append(": ");
    write(sb, field.getValue());
  }

  /** Mirrors Rust's {@code {:?}} for a string: double quotes with escaped specials. */
  private static void writeDebugString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    sb.append('"');
  }
}
