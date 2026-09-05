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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionsTest {

  private static Step step(String action) {
    return new Step(action, NondetPicks.of(Record.of("n", Int.of(5))), Record.of());
  }

  @Test
  void matchedHandlerRuns() throws Exception {
    List<String> log = new ArrayList<>();
    Actions.on(step("MoveX"))
        .action("init", () -> log.add("init"))
        .action("MoveX", s -> log.add("MoveX " + s.pick("n").display()))
        .ignore("stuttered")
        .run();
    assertEquals(List.of("MoveX 5"), log);
  }

  @Test
  void ignoredActionIsNoop() throws Exception {
    List<String> log = new ArrayList<>();
    Actions.on(step("stuttered")).action("init", () -> log.add("init")).ignore("stuttered").run();
    assertEquals(List.of(), log);
  }

  @Test
  void unmatchedActionThrows() {
    Actions actions = Actions.on(step("Unknown")).action("init", () -> {}).ignore("stuttered");
    DriverException e = assertThrows(DriverException.class, actions::run);
    assertEquals("Unimplemented action `Unknown`", e.getMessage());
  }

  @Test
  void otherwiseCatchesUnmatched() throws Exception {
    List<String> log = new ArrayList<>();
    Actions.on(step("Unknown"))
        .action("init", () -> log.add("init"))
        .otherwise(s -> log.add("other " + s.action()))
        .run();
    assertEquals(List.of("other Unknown"), log);
  }

  @Test
  void handlerExceptionsPropagate() {
    Actions actions =
        Actions.on(step("init"))
            .action(
                "init",
                () -> {
                  throw new IllegalStateException("boom");
                });
    IllegalStateException e = assertThrows(IllegalStateException.class, actions::run);
    assertEquals("boom", e.getMessage());
  }

  @Test
  void duplicateHandlerRejected() {
    Actions actions = Actions.on(step("init")).action("init", () -> {});
    assertThrows(IllegalArgumentException.class, () -> actions.action("init", () -> {}));
    assertThrows(IllegalArgumentException.class, () -> actions.ignore("init"));
  }
}
