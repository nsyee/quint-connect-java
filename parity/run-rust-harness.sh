#!/usr/bin/env bash
# Copyright 2026 the quint-connect-java authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Runs one test of the Rust reference harness (quint-connect v0.1.2) and
# captures everything the Java parity tests compare against:
#
#   <out>/commands.txt      the quint command line(s) the Rust crate spawned
#   <out>/run_<n>.itf.json  the ITF trace(s) it replayed
#   <out>/stderr.txt        its raw stderr (ANSI colors disabled)
#   <out>/status.txt        0 when the Rust test passed, 1 when it failed
#
# Usage: run-rust-harness.sh <test-name> <out-dir> [seed] [verbosity]
#   test-name  tictactoe | tictactoe_diverging (see rust-harness/tests)
#   seed       QUINT_SEED, default 0x2a
#   verbosity  QUINT_VERBOSE, default 1
#
# QUINT_SEED / QUINT_VERBOSE are compile-time constants in the Rust crate
# (`option_env!`), so the harness is rebuilt whenever they change.
set -euo pipefail

test_name="${1:?test name}"
out="${2:?output directory}"
seed="${3:-0x2a}"
verbosity="${4:-1}"

parity_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
harness="$parity_dir/rust-harness"

mkdir -p "$out"
out="$(cd "$out" && pwd)"
rm -f "$out"/commands.txt "$out"/*.itf.json "$out"/stderr.txt "$out"/status.txt

export QUINT_SEED="$seed"
export QUINT_VERBOSE="$verbosity"
export QUINT_CAPTURE_DIR="$out"
export NO_COLOR=1
export PATH="$parity_dir/bin:$PATH"

cd "$harness"
cargo build --tests --quiet

status=0
cargo test --quiet --test tictactoe -- --exact "$test_name" --nocapture --test-threads=1 \
  >"$out/cargo-stdout.txt" 2>"$out/cargo-stderr.txt" || status=$?

# libtest prints its own lines ("running 1 test", "test … ok") on stdout;
# quint-connect logs to stderr, which is what we keep.
cp "$out/cargo-stderr.txt" "$out/stderr.txt"

# `#[should_panic]` tests pass from cargo's point of view when the runner
# failed; report the runner's outcome instead.
if grep -q '^   \[OK\] ' "$out/stderr.txt"; then
  echo 0 >"$out/status.txt"
else
  echo 1 >"$out/status.txt"
fi

if [[ "$status" -ne 0 ]]; then
  echo "cargo test exited with $status; see $out/cargo-stderr.txt" >&2
  exit "$status"
fi
