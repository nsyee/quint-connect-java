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
package io.github.nsyee.quintconnect.log;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;

/**
 * The runner's console logger, a port of the {@code logger} module of the Rust crate.
 *
 * <p>Messages go to {@link System#err} by default. {@link #title} is printed bold with a {@code ==
 * } prefix; every other level indents each line of the message by three spaces. {@link #trace}
 * messages are only printed when the verbosity is at least the requested level; the verbosity is
 * read from the {@code QUINT_VERBOSE} system property or environment variable (default {@code 0}).
 * ANSI colors are disabled when {@code NO_COLOR} is set, forced when {@code FORCE_COLOR} is set and
 * otherwise enabled when {@code TERM} names a terminal other than {@code dumb}.
 */
public final class Logger {

  /** Name of the system property / environment variable holding the verbosity (0, 1 or 2). */
  public static final String QUINT_VERBOSE = "QUINT_VERBOSE";

  /** Name of the environment variable that disables colored output when set. */
  public static final String NO_COLOR = "NO_COLOR";

  /** Name of the environment variable that forces colored output when set. */
  public static final String FORCE_COLOR = "FORCE_COLOR";

  private static final int INDENT = 3;

  private static final String RESET = "\u001b[0m";
  private static final String BOLD = "\u001b[1m";
  private static final String DIM = "\u001b[2m";
  private static final String RED = "\u001b[31m";
  private static final String GREEN = "\u001b[32m";
  private static final String BRIGHT_WHITE = "\u001b[97m";

  private final PrintStream out;
  private final int verbosity;
  private final boolean color;

  /**
   * Creates a logger.
   *
   * @param out where messages are written
   * @param verbosity the level up to which {@link #trace} messages are printed
   * @param color whether to emit ANSI color codes
   */
  public Logger(PrintStream out, int verbosity, boolean color) {
    this.out = Objects.requireNonNull(out, "out");
    this.verbosity = verbosity;
    this.color = color;
  }

  /**
   * The logger used by default: standard error, verbosity from {@code QUINT_VERBOSE}, colors as
   * described in the class documentation.
   */
  public static Logger standard() {
    return new Logger(System.err, resolveVerbosity(), colorEnabled());
  }

  /** A logger that never prints anything. */
  public static Logger silent() {
    return new Logger(new PrintStream(PrintStream.nullOutputStream()), 0, false);
  }

  /** Returns a copy writing to {@code out}. */
  public Logger withOutput(PrintStream out) {
    return new Logger(out, verbosity, color);
  }

  /** Returns a copy with the given verbosity. */
  public Logger withVerbosity(int verbosity) {
    return new Logger(out, verbosity, color);
  }

  /** Returns a copy with colors switched on or off. */
  public Logger withColor(boolean color) {
    return new Logger(out, verbosity, color);
  }

  /** The verbosity level of this logger. */
  public int verbosity() {
    return verbosity;
  }

  /** Whether a {@link #trace} message at {@code level} would be printed. */
  public boolean isEnabled(int level) {
    return verbosity >= level;
  }

  /** Prints {@code == message} in bold. */
  public void title(String message) {
    out.println(paint("== " + message, BOLD));
  }

  /** Prints an indented message. */
  public void info(String message) {
    out.println(indent(INDENT, message));
  }

  /** Prints an indented message in bold green. */
  public void success(String message) {
    out.println(paint(indent(INDENT, message), BOLD, GREEN));
  }

  /** Prints an indented message in bold red. */
  public void error(String message) {
    out.println(paint(indent(INDENT, message), BOLD, RED));
  }

  /** Prints an indented, dimmed message if the verbosity is at least {@code level}. */
  public void trace(int level, String message) {
    if (isEnabled(level)) {
      out.println(paint(indent(INDENT, message), DIM, BRIGHT_WHITE));
    }
  }

  private String paint(String text, String... codes) {
    if (!color) {
      return text;
    }
    return String.join("", codes) + text + RESET;
  }

  /**
   * Prefixes every line of {@code text} with {@code level} spaces, like the Rust {@code indent!}
   * macro: line terminators are preserved and an empty text stays empty.
   */
  public static String indent(int level, String text) {
    if (text.isEmpty()) {
      return text;
    }
    String prefix = " ".repeat(level);
    StringBuilder sb = new StringBuilder(text.length() + prefix.length() * 4);
    int start = 0;
    while (start < text.length()) {
      int nl = text.indexOf('\n', start);
      int end = nl < 0 ? text.length() : nl + 1;
      sb.append(prefix).append(text, start, end);
      start = end;
    }
    return sb.toString();
  }

  /** Reads the verbosity from the {@code QUINT_VERBOSE} system property, then the environment. */
  public static int resolveVerbosity() {
    return resolveVerbosity(
        Optional.ofNullable(System.getProperty(QUINT_VERBOSE)),
        Optional.ofNullable(System.getenv(QUINT_VERBOSE)));
  }

  static int resolveVerbosity(Optional<String> property, Optional<String> env) {
    return property
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .or(() -> env.map(String::strip).filter(s -> !s.isEmpty()))
        .map(Logger::parseLevel)
        .orElse(0);
  }

  private static int parseLevel(String value) {
    try {
      return Math.max(0, Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static boolean colorEnabled() {
    return colorEnabled(
        Optional.ofNullable(System.getenv(NO_COLOR)),
        Optional.ofNullable(System.getenv(FORCE_COLOR)),
        Optional.ofNullable(System.getenv("TERM")));
  }

  static boolean colorEnabled(
      Optional<String> noColor, Optional<String> forceColor, Optional<String> term) {
    if (noColor.isPresent()) {
      return false;
    }
    if (forceColor.isPresent()) {
      return true;
    }
    return term.filter(t -> !t.isEmpty() && !t.equals("dumb")).isPresent();
  }
}
