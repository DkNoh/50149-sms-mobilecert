#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: tools/orca/start-coordinator.sh <orca-run-id>" >&2
  exit 2
fi

run_id="$1"
case "$run_id" in
  run_[A-Za-z0-9]*) ;;
  *)
    echo "Invalid Orca Run ID: $run_id" >&2
    exit 2
    ;;
esac

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
prompt_content=$(sed "s/{{ORCA_RUN_ID}}/$run_id/g" "$script_dir/coordinator-prompt.md")

exec /home/dk/.local/bin/codex \
  --cd "$repo_root" \
  --dangerously-bypass-approvals-and-sandbox \
  "$prompt_content"
