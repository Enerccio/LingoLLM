#!/bin/sh
# Runs as root only to make the data folder writable for the jetty user, then starts Jetty as jetty.
#
# The data folder is usually a bind mount (./data in docker-compose.yml). When Docker creates a missing ./data on a
# Linux host it is owned by root, and the bind mount hides the folder prepared in the image, so jetty couldn't write
# the database. Ownership is changed only when the folder isn't owned by jetty yet, so a large data folder isn't
# walked on every start.
set -e

DATA_DIR="${LINGOLLM_DATA:-/var/lingollm/.lingollm}"

# the jetty image's own entrypoint (prepares the start command), run after this one
if [ -x /docker-entrypoint.sh ]; then
    set -- /docker-entrypoint.sh "$@"
fi

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$DATA_DIR"
    jetty_uid="$(id -u jetty)"
    if [ "$(stat -c %u "$DATA_DIR")" != "$jetty_uid" ]; then
        echo "Changing owner of $DATA_DIR to jetty ($jetty_uid:$(id -g jetty))"
        chown -R jetty:jetty "$DATA_DIR"
    fi
    exec setpriv --reuid=jetty --regid=jetty --init-groups "$@"
fi

# started with another user (docker run --user / compose user:), the folder must already be writable for it
exec "$@"
