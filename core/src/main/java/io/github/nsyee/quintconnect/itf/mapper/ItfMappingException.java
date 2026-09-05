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

/** Thrown when an {@code ItfValue} cannot be mapped to a Java type or vice versa. */
public class ItfMappingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The hint the Rust crate appends when the specification state cannot be deserialised. */
  public static final String STATE_HINT =
      "Failed to deserialize specification's state.\n"
          + "Please check the docs for tips and tricks on state deserialization.";

  /** Creates an exception with the given message. */
  public ItfMappingException(String message) {
    super(message);
  }

  /** Creates an exception with the given message and cause. */
  public ItfMappingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Wraps {@code cause} with {@link #STATE_HINT}, keeping the detailed message as a second line.
   */
  public static ItfMappingException forState(Throwable cause) {
    return new ItfMappingException(STATE_HINT + "\n\nCaused by: " + cause.getMessage(), cause);
  }
}
