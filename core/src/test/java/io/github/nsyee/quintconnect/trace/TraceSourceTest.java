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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceSourceTest {

  private static final String EMPTY_TRACE = "{\"vars\": [], \"states\": []}";

  @Test
  void ordersBySequenceNumber(@TempDir Path dir) throws Exception {
    for (String name :
        List.of("run_10.itf.json", "run_2.itf.json", "run_1.itf.json", "notes.txt")) {
      Files.writeString(dir.resolve(name), EMPTY_TRACE);
    }
    TraceSource source = TraceSource.ofDirectory(dir);
    assertEquals(
        List.of("run_1.itf.json", "run_2.itf.json", "run_10.itf.json"),
        source.files().stream().map(p -> p.getFileName().toString()).toList());
    assertEquals(3, source.toList().size());
  }

  @Test
  void filesWithoutSequenceSortLast() {
    List<Path> files =
        List.of(Path.of("zzz.itf.json"), Path.of("run_3.itf.json"), Path.of("abc.itf.json"));
    assertEquals(
        List.of(Path.of("run_3.itf.json"), Path.of("abc.itf.json"), Path.of("zzz.itf.json")),
        TraceSource.ofFiles(files).files());
  }

  @Test
  void closeDeletesTempDirectoryOnly(@TempDir Path root) throws Exception {
    Path temp = Files.createDirectory(root.resolve("temp"));
    Files.writeString(temp.resolve("run_0.itf.json"), EMPTY_TRACE);
    Files.createDirectories(temp.resolve("nested"));
    Path kept = Files.createDirectory(root.resolve("kept"));
    Files.writeString(kept.resolve("run_0.itf.json"), EMPTY_TRACE);

    try (TraceSource source = TraceSource.ofTempDirectory(temp)) {
      assertEquals(1, source.size());
    }
    assertFalse(Files.exists(temp));

    try (TraceSource source = TraceSource.ofDirectory(kept)) {
      assertEquals(1, source.size());
    }
    assertTrue(Files.exists(kept.resolve("run_0.itf.json")));
  }
}
