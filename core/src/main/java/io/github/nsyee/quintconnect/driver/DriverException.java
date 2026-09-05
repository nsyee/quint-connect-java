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

/**
 * Thrown when a trace state cannot be turned into a {@link Step} (missing or malformed {@code
 * mbt::*} variables, bad {@link DriverConfig} paths) or when a driver asks for an action or a
 * nondet pick the trace does not provide.
 *
 * <p>Messages are kept identical to the Rust crate.
 */
public class DriverException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates an exception with the given message. */
  public DriverException(String message) {
    super(message);
  }

  /** Creates an exception with the given message and cause. */
  public DriverException(String message, Throwable cause) {
    super(message, cause);
  }
}
