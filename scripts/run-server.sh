#!/usr/bin/env bash
set -euo pipefail

# PHASE 3 - Added in Phase 3
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

# PHASE 3 - Added in Phase 3
gson_jar="${GSON_JAR:-${HOME}/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar}"
if [[ ! -d target/classes || ! -f "$gson_jar" ]]; then
  echo "Build first with ./scripts/verify.sh (or set GSON_JAR)." >&2
  exit 1
fi

# PHASE 3 - Added in Phase 3
exec java -cp "target/classes:$gson_jar" \
  civilization.server.bootstrap.CivilizationServerApplication "$@"
