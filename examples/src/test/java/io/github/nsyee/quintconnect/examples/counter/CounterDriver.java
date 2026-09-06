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
package io.github.nsyee.quintconnect.examples.counter;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.DriverConfig;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;
import io.github.nsyee.quintconnect.itf.mapper.ItfVariant;

/**
 * Connects {@link Counter} to {@code spec/Counter.tla}.
 *
 * <p>Apalache has no {@code --mbt} mode, so the specification records the action it took in the
 * {@code mbt_action_taken} variable, shaped like a Quint sum type ({@code [tag |-> "Increment",
 * value |-> [amount |-> 2]]}). {@link #config()} points the framework at that variable; since it is
 * a regular state variable, it is also part of the state this driver reproduces.
 */
final class CounterDriver implements Driver<CounterDriver.CounterState> {

  /** {@code mbt_action_taken}: one variant per action, each carrying {@code amount}. */
  sealed interface Action permits Init, Increment, Decrement, Reset {}

  @ItfVariant(record = true)
  record Init(long amount) implements Action {}

  @ItfVariant(record = true)
  record Increment(long amount) implements Action {}

  @ItfVariant(record = true)
  record Decrement(long amount) implements Action {}

  @ItfVariant(record = true)
  record Reset(long amount) implements Action {}

  /** The specification's state variables. */
  record CounterState(long count, @JsonProperty("mbt_action_taken") Action actionTaken) {}

  private final ItfMapper mapper = ItfMapper.defaultMapper();
  private Counter counter = new Counter();
  private Action lastAction = new Init(0);

  @Override
  public void step(Step step) {
    switch (step.action()) {
      case "Init" -> {
        counter = new Counter();
        lastAction = new Init(0);
      }
      case "Increment" -> {
        long n = step.pick("amount", mapper.to(Long.class));
        counter.increment(n);
        lastAction = new Increment(n);
      }
      case "Decrement" -> {
        long n = step.pick("amount", mapper.to(Long.class));
        counter.decrement(n);
        lastAction = new Decrement(n);
      }
      case "Reset" -> {
        counter.reset();
        lastAction = new Reset(0);
      }
      default -> throw Step.unimplemented(step);
    }
  }

  @Override
  public CounterState state() {
    return new CounterState(counter.count(), lastAction);
  }

  @Override
  public Class<CounterState> stateType() {
    return CounterState.class;
  }

  @Override
  public DriverConfig config() {
    return DriverConfig.DEFAULT.nondetPath("mbt_action_taken");
  }
}
