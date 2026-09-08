// Copyright 2026 the quint-connect-java authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Reference runs. `QUINT_SEED` / `QUINT_VERBOSE` are read by quint-connect at
//! *compile* time, so `cargo test` must be (re)built with them exported.
//! The test names double as the `test_name` printed in the logs.

use quint_connect::*;
use quint_connect_parity_harness::{Correct, Diverging};


#[quint_run(spec = "../../core/src/test/resources/spec/tictactoe.qnt", max_samples = 1)]
fn tictactoe() -> impl Driver {
    Correct::default()
}

#[quint_run(spec = "../../core/src/test/resources/spec/tictactoe.qnt", max_samples = 1)]
#[should_panic(expected = "State invariant failed")]
fn tictactoe_diverging() -> impl Driver {
    Diverging::default()
}
