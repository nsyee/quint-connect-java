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

# Regenerates the golden fixtures under core/src/test/resources/parity from the
# Rust reference implementation (quint-connect v0.1.2, seed 0x2a, verbosity 1).
# Requires: cargo (Rust toolchain) and `quint` on PATH. See docs/parity.md.
set -euo pipefail

parity_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixtures="$parity_dir/../core/src/test/resources/parity"
seed="${QUINT_SEED:-0x2a}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

"$parity_dir/run-rust-harness.sh" tictactoe "$work/ok" "$seed" 1
"$parity_dir/run-rust-harness.sh" tictactoe_diverging "$work/diverging" "$seed" 1

# Only `#meta` (timestamps) may differ between the two runs.
states() {
  python3 -c 'import json, sys; print(json.dumps(json.load(open(sys.argv[1]))["states"], sort_keys=True))' "$1"
}
if [[ "$(states "$work/ok/run_0.itf.json")" != "$(states "$work/diverging/run_0.itf.json")" ]]; then
  echo "the two Rust runs produced different traces for the same seed" >&2
  exit 1
fi

mkdir -p "$fixtures" "$fixtures/../itf/rust"
cp "$work/ok/run_0.itf.json" "$fixtures/../itf/rust/tictactoe_${seed}.itf.json"
cp "$work/ok/commands.txt" "$fixtures/tictactoe_${seed}.command.txt"
cp "$work/ok/stderr.txt" "$fixtures/tictactoe_${seed}.ok.stderr.txt"
cp "$work/diverging/stderr.txt" "$fixtures/tictactoe_${seed}.diverging.stderr.txt"

{
  echo "quint-connect (Rust): $(grep -A1 '^name = "quint-connect"$' "$parity_dir/rust-harness/Cargo.lock" | sed -n 's/^version = "\(.*\)"/\1/p')"
  echo "quint: $(quint --version)"
  echo "rustc: $(rustc --version)"
  echo "seed: $seed"
  echo "verbosity: 1"
  echo "generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >"$fixtures/PROVENANCE.txt"

echo "golden fixtures written to $fixtures"
