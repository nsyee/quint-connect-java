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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuintCliTest {

  @Test
  void defaultExecutablePerOs() {
    assertEquals("quint", QuintCli.locate(Optional.empty(), false).executable());
    assertEquals("quint.cmd", QuintCli.locate(Optional.empty(), true).executable());
  }

  @Test
  void quintBinOverridesDefault() {
    assertEquals("/opt/quint", QuintCli.locate(Optional.of("/opt/quint"), true).executable());
    assertEquals("quint", QuintCli.locate(Optional.of("  "), false).executable());
  }

  @Test
  void detectsWindows() {
    assertTrue(QuintCli.isWindows("Windows 11"));
    assertFalse(QuintCli.isWindows("Linux"));
    assertFalse(QuintCli.isWindows("Mac OS X"));
  }

  @Test
  void commandPrependsExecutable() {
    List<String> cmd =
        QuintCli.of("my-quint").command(RunConfig.of(Path.of("foo.qnt"), "1"), Path.of("out"));
    assertEquals("my-quint", cmd.get(0));
    assertEquals("run", cmd.get(1));
    assertEquals("foo.qnt", cmd.get(2));
  }

  @Test
  void resolvesOnPath(@TempDir Path dir) throws Exception {
    Path bin = dir.resolve("bin");
    Files.createDirectories(bin);
    Path exe = bin.resolve("quint");
    Files.writeString(exe, "");
    String path = dir.resolve("empty") + File.pathSeparator + bin;

    assertEquals(Optional.of(exe), QuintCli.of("quint").resolve(path, null));
    assertEquals(Optional.empty(), QuintCli.of("quint").resolve(dir.toString(), null));
    assertEquals(Optional.empty(), QuintCli.of("quint").resolve(null, null));
  }

  @Test
  void resolvesWithPathExt(@TempDir Path dir) throws Exception {
    Path exe = dir.resolve("quint.CMD");
    Files.writeString(exe, "");
    assertEquals(
        Optional.of(exe),
        QuintCli.of("quint")
            .resolve(dir.toString(), ".EXE;.CMD".replace(';', File.pathSeparatorChar)));
  }

  @Test
  void resolvesExplicitPath(@TempDir Path dir) throws Exception {
    Path exe = dir.resolve("quint");
    assertEquals(Optional.empty(), QuintCli.of(exe.toString()).resolve("", null));
    Files.writeString(exe, "");
    assertEquals(Optional.of(exe), QuintCli.of(exe.toString()).resolve("", null));
  }
}
