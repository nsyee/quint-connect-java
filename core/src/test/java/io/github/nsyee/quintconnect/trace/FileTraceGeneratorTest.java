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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfTrace;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileTraceGeneratorTest {

  static Path fixture(String name) {
    try {
      return Path.of(FileTraceGeneratorTest.class.getResource("/" + name).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void replaysFixturesIgnoringConfig() {
    TraceGenerator generator =
        FileTraceGenerator.of(
            fixture("itf/tictactoe.itf.json"), fixture("itf/two_phase_commit.itf.json"));
    try (TraceSource source = generator.generate(RunConfig.of(Path.of("ignored.qnt"), "0x1"))) {
      List<ItfTrace> traces = source.toList();
      assertEquals(2, traces.size());
      assertTrue(traces.get(0).vars().contains("board"));
      assertTrue(traces.get(0).length() > 0);
    }
  }

  @Test
  void replaysDirectory() {
    TraceGenerator generator = FileTraceGenerator.ofDirectory(fixture("itf"));
    try (TraceSource source = generator.generate(TestConfig.of(Path.of("x.qnt"), "t", "1"))) {
      assertEquals(2, source.size());
    }
  }
}
