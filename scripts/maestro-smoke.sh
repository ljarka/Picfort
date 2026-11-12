#!/usr/bin/env bash
# Runs the Maestro device flows in maestro/ against the test account and the test server.
#
#   ./scripts/maestro-smoke.sh                     # all flows, first connected device
#   DEVICE=emulator-5556 ./scripts/maestro-smoke.sh
#   ./scripts/maestro-smoke.sh maestro/smoke.yaml  # one flow
#
# Needs: maestro (brew install mobile-dev-inc/tap/maestro), the debug app installed
# (./gradlew :app:installDebug), and the picfort_test server on :5051 (docker start picfort_test).
# Credentials stay in ~/.config/picfort/test.env; Maestro reads MAESTRO_* from the environment,
# so nothing is passed on the command line.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${PICFORT_TEST_ENV:-$HOME/.config/picfort/test.env}"

[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE (test account credentials)." >&2; exit 1; }
command -v maestro >/dev/null || { echo "maestro not found: brew install mobile-dev-inc/tap/maestro" >&2; exit 1; }

set -a; source "$ENV_FILE"; set +a

export MAESTRO_IMMICH_URL="$PICFORT_TEST_IMMICH_URL"
export MAESTRO_IMMICH_EMAIL="$PICFORT_TEST_IMMICH_EMAIL"
export MAESTRO_IMMICH_PASSWORD="$PICFORT_TEST_IMMICH_PASSWORD"
export MAESTRO_PICFORT_SERVER="http://$PICFORT_TEST_SERVER_ADDRESS"

if ! curl -s -m 3 -o /dev/null "http://localhost:$PICFORT_TEST_SERVER_PORT/"; then
  echo "Test server not reachable on :$PICFORT_TEST_SERVER_PORT. Start it: docker start picfort_test" >&2
  exit 1
fi

DEVICE_ARGS=()
[ -n "${DEVICE:-}" ] && DEVICE_ARGS=(--device "$DEVICE")

if [ $# -eq 0 ]; then set -- "$ROOT/maestro"; fi
exec maestro "${DEVICE_ARGS[@]+"${DEVICE_ARGS[@]}"}" test "$@"
