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

/**
 * The user-facing driver API: {@link io.github.nsyee.quintconnect.driver.Driver} connects an
 * implementation to a specification, {@link io.github.nsyee.quintconnect.driver.Step} carries one
 * trace step (action, nondet picks, specification state) extracted by {@link
 * io.github.nsyee.quintconnect.driver.StepExtractor} according to a {@link
 * io.github.nsyee.quintconnect.driver.DriverConfig}, and {@link
 * io.github.nsyee.quintconnect.driver.Actions} dispatches actions to handlers.
 */
package io.github.nsyee.quintconnect.driver;
