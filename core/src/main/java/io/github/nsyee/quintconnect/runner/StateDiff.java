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
package io.github.nsyee.quintconnect.runner;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import io.github.nsyee.quintconnect.itf.ItfValue;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A divergence between the specification state and the implementation state, rendered as a unified
 * diff with the headers {@code specification} and {@code implementation}.
 *
 * <p>Both sides are pretty-printed one element per line (like Rust's {@code {:#?}}) so the diff
 * points at the differing fields rather than at two long lines.
 *
 * @param specification the pretty-printed specification state
 * @param implementation the pretty-printed implementation state
 */
public record StateDiff(String specification, String implementation) {

  /** Context lines around each change; large enough to show whole states. */
  static final int CONTEXT = 256;

  /** Creates a diff between two states. */
  public StateDiff {
    Objects.requireNonNull(specification, "specification");
    Objects.requireNonNull(implementation, "implementation");
  }

  /** Creates a diff between two ITF values, pretty-printing both with {@link #pretty}. */
  public static StateDiff of(ItfValue specification, ItfValue implementation) {
    return new StateDiff(pretty(specification), pretty(implementation));
  }

  /**
   * The unified diff, e.g.
   *
   * <pre>
   * --- specification
   * +++ implementation
   * @@ -1,4 +1,4 @@
   *  {
   * -  nextTurn: X,
   * +  nextTurn: O,
   *  }
   * </pre>
   */
  public String unified() {
    List<String> left = specification.lines().toList();
    List<String> right = implementation.lines().toList();
    List<String> lines =
        UnifiedDiffUtils.generateUnifiedDiff(
            "specification", "implementation", left, DiffUtils.diff(left, right), CONTEXT);
    return String.join("\n", lines);
  }

  @Override
  public String toString() {
    return unified();
  }

  /**
   * Renders {@code value} in Quint-like syntax over several lines: scalars and empty collections on
   * one line, every element of a non-empty collection or record on its own line, indented by two
   * spaces per nesting level and followed by a comma.
   */
  public static String pretty(ItfValue value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value, 0);
    return sb.toString();
  }

  private static void write(StringBuilder sb, ItfValue value, int depth) {
    switch (value) {
      case ItfValue.Bool b -> sb.append(b.display());
      case ItfValue.Int n -> sb.append(n.display());
      case ItfValue.Str s -> sb.append(s.display());
      case ItfValue.Unserializable u -> sb.append(u.display());
      case ItfValue.List(var items) -> writeItems(sb, "List(", items, ")", depth);
      case ItfValue.Tuple(var items) -> writeItems(sb, "(", items, ")", depth);
      case ItfValue.Set(var items) -> writeItems(sb, "Set(", items, ")", depth);
      case ItfValue.Map map -> writeMap(sb, map, depth);
      case ItfValue.Record rec -> writeRecord(sb, rec, depth);
    }
  }

  private static void writeItems(
      StringBuilder sb, String open, List<ItfValue> items, String close, int depth) {
    sb.append(open);
    if (!items.isEmpty()) {
      for (ItfValue item : items) {
        newline(sb, depth + 1);
        write(sb, item, depth + 1);
        sb.append(',');
      }
      newline(sb, depth);
    }
    sb.append(close);
  }

  private static void writeMap(StringBuilder sb, ItfValue.Map map, int depth) {
    sb.append("Map(");
    if (!map.entries().isEmpty()) {
      for (ItfValue.Map.Entry entry : map.entries()) {
        newline(sb, depth + 1);
        write(sb, entry.key(), depth + 1);
        sb.append(" -> ");
        write(sb, entry.value(), depth + 1);
        sb.append(',');
      }
      newline(sb, depth);
    }
    sb.append(')');
  }

  private static void writeRecord(StringBuilder sb, ItfValue.Record rec, int depth) {
    if (rec.variantTag().isPresent()) {
      writeVariant(sb, rec.variantTag().get(), rec.get("value").orElseThrow(), depth);
      return;
    }
    if (rec.size() == 0) {
      sb.append("{}");
      return;
    }
    sb.append('{');
    Iterator<Map.Entry<String, ItfValue>> it = rec.fields().entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, ItfValue> field = it.next();
      newline(sb, depth + 1);
      sb.append(field.getKey()).append(": ");
      write(sb, field.getValue(), depth + 1);
      sb.append(',');
    }
    newline(sb, depth);
    sb.append('}');
  }

  private static void writeVariant(StringBuilder sb, String tag, ItfValue value, int depth) {
    sb.append(tag);
    if (value instanceof ItfValue.Tuple tuple) {
      if (!tuple.isEmpty()) {
        writeItems(sb, "(", tuple.items(), ")", depth);
      }
    } else {
      writeItems(sb, "(", List.of(value), ")", depth);
    }
  }

  private static void newline(StringBuilder sb, int depth) {
    sb.append('\n').append("  ".repeat(depth));
  }
}
