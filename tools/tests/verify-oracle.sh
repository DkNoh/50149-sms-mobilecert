#!/usr/bin/env bash
# Local Docker Oracle integration tests. Never reads production configuration.
set -euo pipefail
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python3 "$script_dir/oracle-integration.py" "$@"
