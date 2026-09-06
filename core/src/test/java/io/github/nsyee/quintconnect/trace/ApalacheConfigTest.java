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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApalacheConfigTest {

  private static final String PREFIX =
      "apalache-mc --run-dir=tmpdir --out-dir=tmpdir/_apalache-out ";

  private static String toString(GenConfig config) {
    return "apalache-mc "
        + String.join(" ", config.toCommand(Path.of("tmpdir"))).replace('\\', '/');
  }

  @Test
  void simulateBasic() {
    ApalacheConfig config = ApalacheConfig.simulate(Path.of("Foo.tla"), "42");
    assertEquals(100, config.nTraces());
    assertEquals(
        PREFIX
            + "simulate --output-traces --tuning-options=smt.randomSeed=42 --max-run=100 Foo.tla",
        toString(config));
  }

  @Test
  void simulateAllOptions() {
    ApalacheConfig config =
        ApalacheConfig.simulate(Path.of("Foo.tla"), "0x2a")
            .withInit("MyInit")
            .withNext("MyNext")
            .withCinit("ConstInit")
            .withInvariants("Inv1", "Inv2")
            .withLength(7)
            .withMaxRuns(3);
    assertEquals(3, config.nTraces());
    assertEquals(
        PREFIX
            + "simulate --output-traces --tuning-options=smt.randomSeed=42 --init=MyInit"
            + " --next=MyNext --cinit=ConstInit --inv=Inv1,Inv2 --length=7 --max-run=3 Foo.tla",
        toString(config));
  }

  @Test
  void checkBasic() {
    ApalacheConfig config = ApalacheConfig.check(Path.of("Foo.tla"), "1").withInvariants("Inv");
    assertEquals(1, config.nTraces());
    assertEquals(
        PREFIX + "check --tuning-options=smt.randomSeed=1 --inv=Inv Foo.tla", toString(config));
  }

  @Test
  void checkWithMaxErrors() {
    ApalacheConfig config =
        ApalacheConfig.check(Path.of("Foo.tla"), "1")
            .withInvariants("Inv")
            .withLength(4)
            .withMaxErrors(5, "View");
    assertEquals(5, config.nTraces());
    assertEquals(
        PREFIX
            + "check --tuning-options=smt.randomSeed=1 --inv=Inv --length=4 --max-error=5"
            + " --view=View Foo.tla",
        toString(config));
  }

  @Test
  void seedIsReducedToNonNegative31Bits() {
    assertEquals(42, ApalacheConfig.simulate(Path.of("F.tla"), "0x2a").smtSeed());
    assertEquals(0, ApalacheConfig.simulate(Path.of("F.tla"), "0x80000000").smtSeed());
    assertEquals(1, ApalacheConfig.simulate(Path.of("F.tla"), "0x80000001").smtSeed());
    assertEquals((1L << 31) - 1, ApalacheConfig.simulate(Path.of("F.tla"), "-1").smtSeed());
    assertEquals(7, ApalacheConfig.simulate(Path.of("F.tla"), "7").withSeed("0x7").smtSeed());
  }

  @Test
  void nonIntegerSeedIsRejected() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> ApalacheConfig.simulate(Path.of("F.tla"), "abc"));
    assertTrue(e.getMessage().contains("abc"), e.getMessage());
  }

  @Test
  void quintCliRejectsApalacheConfig() {
    ApalacheConfig config = ApalacheConfig.simulate(Path.of("F.tla"), "1");
    assertThrows(
        IllegalArgumentException.class, () -> QuintCli.of("quint").command(config, Path.of("o")));
    assertThrows(
        IllegalArgumentException.class,
        () -> ApalacheTraceGenerator.create().generate(RunConfig.of(Path.of("f.qnt"), "1")));
  }
}
