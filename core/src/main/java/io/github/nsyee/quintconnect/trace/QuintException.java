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
package io.github.nsyee.quintconnect.trace;

import java.util.Objects;

/**
 * Thrown when the Quint CLI cannot be executed or exits with a non-zero code.
 *
 * <p>For a non-zero exit the message is {@code "Quint returned non-zero code."}, as in the Rust
 * crate, and {@link #stderr()} holds what Quint printed.
 */
public class QuintException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String stderr;

  /** Creates an exception with the given message and no captured output. */
  public QuintException(String message) {
    this(message, "", null);
  }

  /** Creates an exception with the given message and cause. */
  public QuintException(String message, Throwable cause) {
    this(message, "", cause);
  }

  /** Creates an exception carrying Quint's standard error output. */
  public QuintException(String message, String stderr, Throwable cause) {
    super(message, cause);
    this.stderr = Objects.requireNonNull(stderr, "stderr");
  }

  /** Standard error output of the Quint process, or an empty string if not applicable. */
  public String stderr() {
    return stderr;
  }

  @Override
  public String getMessage() {
    String message = super.getMessage();
    return stderr.isEmpty() ? message : message + System.lineSeparator() + stderr;
  }
}
