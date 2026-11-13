#!/bin/sh
# Starts the Picfort server without root rights.
#
# The container starts as root only for this step: data written by older images (which ran as
# root) is handed over to the "picfort" user, then the server runs as that user. Started with
# `docker run --user ...` instead, the server runs as that user directly.
set -e

if [ "$(id -u)" = "0" ]; then
    mkdir -p /data
    # Only files that aren't ours yet, so later starts don't walk every photo again.
    find /data ! -user picfort -exec chown picfort:picfort {} + 2>/dev/null || true
    exec setpriv --reuid=picfort --regid=picfort --init-groups --inh-caps=-all "$@"
fi
exec "$@"
