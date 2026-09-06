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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApalacheCliTest {

  @Test
  void defaultExecutablePerOs() {
    assertEquals("apalache-mc", ApalacheCli.locate(Optional.empty(), false).executable());
    assertEquals("apalache-mc.bat", ApalacheCli.locate(Optional.empty(), true).executable());
  }

  @Test
  void apalacheBinOverridesDefault() {
    assertEquals(
        "/opt/apalache-mc", ApalacheCli.locate(Optional.of("/opt/apalache-mc"), true).executable());
    assertEquals("apalache-mc", ApalacheCli.locate(Optional.of("  "), false).executable());
  }

  @Test
  void commandPrependsExecutable() {
    List<String> cmd =
        ApalacheCli.of("my-apalache")
            .command(ApalacheConfig.simulate(Path.of("Foo.tla"), "1"), Path.of("out"));
    assertEquals("my-apalache", cmd.get(0));
    assertEquals("Foo.tla", cmd.get(cmd.size() - 1));
    assertTrue(cmd.contains("simulate"));
  }

  @Test
  void resolvesOnPath(@TempDir Path dir) throws Exception {
    Path exe = dir.resolve("apalache-mc");
    Files.writeString(exe, "#!/bin/sh\n");
    assertTrue(exe.toFile().setExecutable(true) || System.getProperty("os.name").contains("Win"));
    assertEquals(Optional.of(exe), ApalacheCli.of("apalache-mc").resolve(dir.toString(), null));
    assertEquals(Optional.empty(), ApalacheCli.of("apalache-mc").resolve(null, null));
    assertEquals(Optional.of(exe), ApalacheCli.of(exe.toString()).resolve("", null));
  }

  @Test
  void missingExecutableIsNotAvailable() {
    ApalacheCli cli = ApalacheCli.of("definitely-not-installed-apalache");
    assertEquals("ApalacheCli[definitely-not-installed-apalache]", cli.toString());
    assertTrue(!cli.isAvailable());
  }
}
