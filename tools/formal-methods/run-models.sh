#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/tla2tools.jar" >&2
  exit 2
fi

jar_path=$1
script_dir=$(cd "$(dirname "$0")" && pwd)
status=0
mkdir -p "$script_dir/states"

for config_path in "$script_dir"/*.cfg; do
  config_name=$(basename "$config_path")
  model_name=${config_name%.cfg}
  case "$config_name" in
    EvaluationFalseAck.cfg) module_name=EvaluationFlush ;;
    EvaluationWriteFailure.cfg) module_name=EvaluationWriteRetry ;;
    *) module_name=$model_name ;;
  esac
  invariant=$(awk '$1 == "INVARIANT" { print $2 }' "$config_path")
  output_path=$(mktemp)
  state_path=$(mktemp -d "$script_dir/states/$model_name.XXXXXX")

  if java -cp "$jar_path" tlc2.TLC \
    -config "$config_path" \
    -metadir "$state_path" \
    "$script_dir/$module_name.tla" >"$output_path" 2>&1; then
    echo "FAIL $config_name: expected counterexample but TLC succeeded" >&2
    status=1
  elif grep -q "Invariant $invariant is violated" "$output_path"; then
    echo "PASS $config_name: expected invariant violation"
  else
    echo "FAIL $config_name: unexpected TLC failure" >&2
    sed -n '1,160p' "$output_path" >&2
    status=1
  fi

  rm -f "$output_path"
  rm -rf "$state_path"
done

exit "$status"
