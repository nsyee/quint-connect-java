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

/** The Apalache sub-command an {@link ApalacheConfig} runs to produce traces. */
public enum ApalacheMode {
  /** {@code apalache-mc simulate --output-traces}: one example trace per random run. */
  SIMULATE("simulate"),
  /** {@code apalache-mc check}: one trace per invariant violation. */
  CHECK("check");

  private final String command;

  ApalacheMode(String command) {
    this.command = command;
  }

  /** The sub-command name on the command line. */
  public String command() {
    return command;
  }
}
