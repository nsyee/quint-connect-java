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
package io.github.nsyee.quintconnect.examples.tictactoe;

import io.github.nsyee.quintconnect.junit.QuintRun;
import io.github.nsyee.quintconnect.junit.TraceReplay;
import org.junit.jupiter.api.Tag;

/** Model-based test of {@link TicTacToe}; needs the Quint CLI on {@code PATH}. */
@Tag("quint")
class TicTacToeMbtTest {

  @QuintRun(spec = "spec/tictactoe.qnt", maxSamples = 10)
  void tictactoe(TraceReplay traces) {
    traces.replay(TicTacToeDriver::new);
  }
}
