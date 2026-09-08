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
package io.github.nsyee.quintconnect.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nsyee.quintconnect.itf.ItfState;
import io.github.nsyee.quintconnect.itf.ItfTrace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers for comparing the output of this port with the Rust reference implementation
 * (quint-connect v0.1.2).
 *
 * <p>Golden fixtures live under {@code src/test/resources/parity} and are produced by {@code
 * parity/regenerate-goldens.sh}; see {@code docs/parity.md}.
 *
 * <p>Two things legitimately differ between the runtimes and are normalised away here:
 *
 * <ul>
 *   <li>Environment-dependent text: ANSI colours, line terminators, temporary directory names and
 *       the {@code thread '…' panicked at …} trailer that Rust's test harness appends after the
 *       runner's own output.
 *   <li>The rendering of the two states inside the unified diff. Rust prints them with {@code
 *       {:#?}} (the {@code Debug} derive of the user's Rust type: field order of the struct,
 *       snake_case names, one line per nesting level), while this port prints a canonical
 *       Quint-like rendering of the ITF value. The diff <em>headers</em> and the <em>changed</em>
 *       lines ({@code -}/{@code +}) are compared after normalising identifiers; context lines are
 *       not.
 * </ul>
 */
public final class Parity {

  private Parity() {}

  /** Reproduction seed used for the committed golden fixtures. */
  public static final String GOLDEN_SEED = "0x2a";

  /** Command line the Rust crate spawned for the golden run, tmp dir replaced by {@code <tmp>}. */
  public static final String GOLDEN_COMMAND = "tictactoe_" + GOLDEN_SEED + ".command.txt";

  /**
   * ITF trace the Rust crate generated and replayed for the golden run (under {@code /itf/rust}).
   */
  public static final String GOLDEN_TRACE = "/itf/rust/tictactoe_" + GOLDEN_SEED + ".itf.json";

  /** Rust stderr for the correct driver ({@code QUINT_VERBOSE=1}). */
  public static final String GOLDEN_OK_LOG = "tictactoe_" + GOLDEN_SEED + ".ok.stderr.txt";

  /** Rust stderr for the diverging driver ({@code QUINT_VERBOSE=1}). */
  public static final String GOLDEN_DIVERGING_LOG =
      "tictactoe_" + GOLDEN_SEED + ".diverging.stderr.txt";

  /** Spec path as the Rust harness passes it to {@code quint} (relative to the harness crate). */
  public static final String HARNESS_SPEC = "../../core/src/test/resources/spec/tictactoe.qnt";

  private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");
  private static final Pattern RUST_PANIC_TRAILER =
      Pattern.compile("\n*thread '.*' (\\(\\d+\\) )?panicked at .*", Pattern.DOTALL);
  private static final Pattern DIFF_HEADER = Pattern.compile("^(---|\\+\\+\\+|@@) .*");
  private static final Pattern SNAKE = Pattern.compile("_([a-z])");

  /**
   * Path of a golden fixture: a bare name resolves under {@code src/test/resources/parity}, an
   * absolute resource path is used as is.
   */
  public static Path golden(String name) {
    String resource = name.startsWith("/") ? name : "/parity/" + name;
    try {
      return Path.of(Parity.class.getResource(resource).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Reads a golden fixture as text with {@code \n} line terminators. */
  public static String goldenText(String name) {
    return read(golden(name));
  }

  /** Reads a file as text with {@code \n} line terminators. */
  public static String read(Path file) {
    try {
      return newlines(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Normalises line terminators to {@code \n}. */
  public static String newlines(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  /** Removes ANSI SGR escape sequences. */
  public static String stripAnsi(String text) {
    return ANSI.matcher(text).replaceAll("");
  }

  /**
   * Normalises a quint-connect log (either runtime): strips colours, unifies line terminators,
   * drops the Rust test-harness panic trailer and trailing blank lines.
   */
  public static String normalizeLog(String log) {
    String text = stripAnsi(newlines(log));
    text = RUST_PANIC_TRAILER.matcher(text).replaceFirst("");
    return text.stripTrailing() + "\n";
  }

  /**
   * Replaces the temporary directory in a captured command line by {@code <tmp>}, so that {@code
   * --out-itf /tmp/quint-connect-XYZ/run_{seq}.itf.json} compares equal across runs.
   */
  public static String normalizeCommand(List<String> command, Path tmpDir) {
    List<String> out = new ArrayList<>();
    for (String arg : command) {
      out.add(
          arg.startsWith(tmpDir.toString())
              ? "<tmp>" + arg.substring(tmpDir.toString().length()).replace('\\', '/')
              : arg.replace('\\', '/'));
    }
    return String.join(" ", out);
  }

  /**
   * Splits a normalised log into the part before the unified diff, the diff itself and the part
   * after it. The diff starts at the {@code --- specification} line and ends before the first blank
   * line that follows it. Logs without a diff yield an empty middle part.
   */
  public record LogParts(String before, String diff, String after) {}

  /** Splits {@code log}, see {@link LogParts}. */
  public static LogParts split(String log) {
    String normalized = normalizeLog(log);
    int start = normalized.indexOf("   --- specification\n");
    if (start < 0) {
      return new LogParts(normalized, "", "");
    }
    int end = normalized.indexOf("\n\n", start);
    if (end < 0) {
      end = normalized.length() - 1;
    }
    return new LogParts(
        normalized.substring(0, start),
        normalized.substring(start, end + 1),
        normalized.substring(end + 1));
  }

  /**
   * Reduces a unified diff to what both runtimes must agree on: the header lines and the changed
   * lines, with indentation removed, trailing commas dropped and {@code snake_case} identifiers
   * rewritten to {@code camelCase}. Context lines are discarded.
   */
  public static List<String> diffEssence(String diff) {
    List<String> essence = new ArrayList<>();
    for (String raw : newlines(diff).lines().toList()) {
      String line = raw.strip();
      if (line.isEmpty()) {
        continue;
      }
      if (DIFF_HEADER.matcher(line).matches()) {
        if (line.startsWith("@@")) {
          essence.add("@@");
        } else {
          essence.add(line);
        }
        continue;
      }
      char sign = line.charAt(0);
      if (sign != '-' && sign != '+') {
        continue;
      }
      String body = line.substring(1).strip();
      if (body.endsWith(",")) {
        body = body.substring(0, body.length() - 1);
      }
      essence.add(sign + " " + camelCase(body));
    }
    return essence;
  }

  /** Rewrites {@code snake_case} identifiers to {@code camelCase}. */
  public static String camelCase(String text) {
    Matcher m = SNAKE.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, m.group(1).toUpperCase(Locale.ROOT));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Asserts that two logs are equivalent: identical outside the unified diff and equal in the
   * diff's {@linkplain #diffEssence essence}.
   */
  public static void assertLogsEquivalent(String expected, String actual) {
    LogParts e = split(expected);
    LogParts a = split(actual);
    assertEquals(e.before(), a.before(), "log before the diff");
    assertEquals(diffEssence(e.diff()), diffEssence(a.diff()), "diff");
    assertEquals(e.after(), a.after(), "log after the diff");
  }

  /**
   * Asserts that two traces have the same states ({@code #meta} is ignored: it carries timestamps
   * and the spec path as given on the command line).
   */
  public static void assertSameStates(ItfTrace expected, ItfTrace actual) {
    assertEquals(expected.vars(), actual.vars(), "vars");
    List<ItfState> e = expected.states();
    List<ItfState> a = actual.states();
    assertEquals(e.size(), a.size(), "number of states");
    for (int i = 0; i < e.size(); i++) {
      assertEquals(e.get(i).index(), a.get(i).index(), "state index " + i);
      assertEquals(e.get(i).value(), a.get(i).value(), "state " + i);
    }
  }
}
