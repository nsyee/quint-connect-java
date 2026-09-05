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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nsyee.quintconnect.itf.ItfValue;
import io.github.nsyee.quintconnect.itf.ItfValue.Int;
import io.github.nsyee.quintconnect.itf.ItfValue.Record;
import io.github.nsyee.quintconnect.itf.ItfValue.Str;
import io.github.nsyee.quintconnect.itf.ItfValue.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of the {@code driver/step.rs} unit tests. */
class StepExtractorTest {

  private static final Record EMPTY = Record.of();

  private static void assertMessageStartsWith(String prefix, Runnable action) {
    DriverException e = assertThrows(DriverException.class, action::run);
    assertTrue(
        e.getMessage().startsWith(prefix),
        () -> "expected message starting with <" + prefix + "> but was <" + e.getMessage() + ">");
  }

  @Test
  void extractValueInPathEmptyPath() {
    Record rec = Record.of("key", new Str("value"));
    assertEquals(rec, StepExtractor.extractValueInPath(rec, List.of()));
  }

  @Test
  void extractValueInPathSingleLevel() {
    Record rec = Record.of("key", new Str("value"));
    assertEquals(new Str("value"), StepExtractor.extractValueInPath(rec, List.of("key")));
  }

  @Test
  void extractValueInPathNested() {
    Record outer = Record.of("outer_key", Record.of("inner_key", Int.of(42)));
    assertEquals(
        Int.of(42), StepExtractor.extractValueInPath(outer, List.of("outer_key", "inner_key")));
  }

  @Test
  void extractValueInPathMissingKey() {
    Record rec = Record.of("key", new Str("value"));
    assertMessageStartsWith(
        "Can not find a value at", () -> StepExtractor.extractValueInPath(rec, List.of("missing")));
  }

  @Test
  void extractValueInPathNonRecord() {
    Record rec = Record.of("key", new Str("value"));
    DriverException e =
        assertThrows(
            DriverException.class,
            () -> StepExtractor.extractValueInPath(rec, List.of("key", "nested")));
    assertEquals(
        "Can not read \"nested\" from non-record value in path: [\"key\", \"nested\"]\n"
            + "Current value: \"value\"",
        e.getMessage());
  }

  @Test
  void findRecordInPathEmptyPath() {
    assertEquals(EMPTY, StepExtractor.findRecordInPath(EMPTY, List.of()));
  }

  @Test
  void findRecordInPathSingleLevel() {
    Record outer = Record.of("inner", EMPTY);
    assertEquals(EMPTY, StepExtractor.findRecordInPath(outer, List.of("inner")));
  }

  @Test
  void findRecordInPathNested() {
    Record outer = Record.of("middle", Record.of("innermost", EMPTY));
    assertEquals(EMPTY, StepExtractor.findRecordInPath(outer, List.of("middle", "innermost")));
  }

  @Test
  void findRecordInPathMissingKey() {
    assertMessageStartsWith(
        "Can not find a Record", () -> StepExtractor.findRecordInPath(EMPTY, List.of("missing")));
  }

  @Test
  void findRecordInPathNonRecord() {
    Record rec = Record.of("key", new Str("value"));
    DriverException e =
        assertThrows(
            DriverException.class, () -> StepExtractor.findRecordInPath(rec, List.of("key")));
    assertEquals(
        "Can not find a Record at \"key\" in path: [\"key\"]\nCurrent state: { key: \"value\" }",
        e.getMessage());
  }

  @Test
  void extractActionFromMbtVarSuccess() {
    Record rec = Record.of("mbt::actionTaken", new Str("TestAction"));
    assertEquals("TestAction", StepExtractor.extractActionFromMbtVar(rec));
  }

  @Test
  void extractActionFromMbtVarMissing() {
    assertMessageStartsWith(
        "Missing `mbt::actionTaken`", () -> StepExtractor.extractActionFromMbtVar(EMPTY));
  }

  @Test
  void extractActionFromMbtVarWrongType() {
    Record rec = Record.of("mbt::actionTaken", Int.of(42));
    assertMessageStartsWith(
        "Failed to decode `mbt::actionTaken`", () -> StepExtractor.extractActionFromMbtVar(rec));
  }

  @Test
  void extractNondetFromMbtVarSuccess() {
    Record rec = Record.of("mbt::nondetPicks", EMPTY);
    assertTrue(StepExtractor.extractNondetFromMbtVar(rec).isEmpty());
  }

  @Test
  void extractNondetFromMbtVarMissing() {
    assertMessageStartsWith(
        "Missing `mbt::nondetPicks`", () -> StepExtractor.extractNondetFromMbtVar(EMPTY));
  }

  @Test
  void extractNondetFromMbtVarWrongType() {
    Record rec = Record.of("mbt::nondetPicks", Int.of(1));
    DriverException e =
        assertThrows(DriverException.class, () -> StepExtractor.extractNondetFromMbtVar(rec));
    assertEquals("Failed to extract nondet picks from trace", e.getMessage());
    assertEquals("Expected nondet picks to be a `Value::Record`", e.getCause().getMessage());
  }

  @Test
  void extractActionFromSumTypeSuccess() {
    Record rec = Record.of("tag", new Str("ActionName"));
    assertEquals("ActionName", StepExtractor.extractActionFromSumType(rec));
  }

  @Test
  void extractActionFromSumTypeMissingTag() {
    assertMessageStartsWith(
        "Expected action to be a sum type variant.",
        () -> StepExtractor.extractActionFromSumType(EMPTY));
  }

  @Test
  void extractActionFromSumTypeWrongType() {
    Record rec = Record.of("tag", Int.of(42));
    assertMessageStartsWith(
        "Expected action to be a sum type variant.",
        () -> StepExtractor.extractActionFromSumType(rec));
  }

  @Test
  void extractNondetFromSumTypeEmptyTuple() {
    Record rec = Record.of("value", Tuple.of());
    assertTrue(StepExtractor.extractNondetFromSumType(rec).isEmpty());
  }

  @Test
  void extractNondetFromSumTypeRecord() {
    Record rec = Record.of("value", Record.of("node", new Str("p1")));
    NondetPicks picks = StepExtractor.extractNondetFromSumType(rec);
    assertEquals(new Str("p1"), picks.get("node").orElseThrow());
  }

  @Test
  void extractNondetFromSumTypeInvalid() {
    Record rec = Record.of("value", new Str("invalid"));
    assertMessageStartsWith(
        "Expected nondet picks to be a sum type variant value as a record.",
        () -> StepExtractor.extractNondetFromSumType(rec));
  }

  @Test
  void extractFromMbtVarsSuccess() {
    Record rec =
        Record.of(
            "mbt::actionTaken", new Str("TestAction"),
            "mbt::nondetPicks", Record.of("pick1", Int.of(1)),
            "state_var", new Str("state_value"));
    Step step = StepExtractor.extractFromMbtVars(rec, List.of("state_var"));
    assertEquals("TestAction", step.action());
    assertEquals(new Str("state_value"), step.specState());
    assertEquals(Int.of(1), step.pick("pick1"));
  }

  @Test
  void extractFromMbtVarsStripsMbtVariables() {
    Record rec =
        Record.of(
            "mbt::actionTaken", new Str("TestAction"), "mbt::nondetPicks", EMPTY, "x", Int.of(1));
    Step step = StepExtractor.extractFromMbtVars(rec, List.of());
    assertEquals(Record.of("x", Int.of(1)), step.specState());
  }

  @Test
  void extractFromMbtVarsMissingAction() {
    Record rec = Record.of("mbt::nondetPicks", EMPTY);
    assertMessageStartsWith(
        "Missing `mbt::actionTaken`", () -> StepExtractor.extractFromMbtVars(rec, List.of()));
  }

  @Test
  void extractFromSumTypeSuccess() {
    Record sum = Record.of("tag", new Str("TestAction"), "value", Tuple.of());
    Record rec = Record.of("sum_type", sum, "state_var", new Str("state_value"));
    Step step = StepExtractor.extractFromSumType(rec, List.of("sum_type"), List.of("state_var"));
    assertEquals("TestAction", step.action());
    assertEquals(new Str("state_value"), step.specState());
    assertTrue(step.picks().isEmpty());
  }

  @Test
  void extractFromSumTypeRemovesMbtVars() {
    Record sum = Record.of("tag", new Str("TestAction"), "value", Tuple.of());
    Record rec =
        Record.of(
            "sum_type",
            sum,
            "mbt::actionTaken",
            new Str("OldAction"),
            "mbt::nondetPicks",
            EMPTY,
            "state_var",
            new Str("state_value"));
    Step step = StepExtractor.extractFromSumType(rec, List.of("sum_type"), List.of());
    assertEquals("TestAction", step.action());
    assertEquals(Record.of("sum_type", sum, "state_var", new Str("state_value")), step.specState());
  }

  @Test
  void extractDispatchesOnConfig() {
    Record sum = Record.of("tag", new Str("Sum"), "value", Tuple.of());
    Record rec =
        Record.of(
            "mbt::actionTaken", new Str("Mbt"),
            "mbt::nondetPicks", EMPTY,
            "s", sum);
    assertEquals("Mbt", StepExtractor.extract(rec, DriverConfig.DEFAULT).action());
    assertEquals("Sum", StepExtractor.extract(rec, DriverConfig.DEFAULT.nondetPath("s")).action());
    ItfValue nested = StepExtractor.extract(rec, DriverConfig.statePath("s", "tag")).specState();
    assertEquals(new Str("Sum"), nested);
  }
}
