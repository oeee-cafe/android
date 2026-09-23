#!/bin/sh
# Copies the site's contract with the apps (appContract.json, beside app_bridge.jinja in
# oeee-cafe/web) to where this app's unit tests read it, so AppContractTest checks the app
# against what the site says now. Run it after the site's contract changes, and commit the
# copy with whatever change to the app it asks for.
#
# The site's checkout is taken to be next to this one; OEEE_CAFE_WEB names it otherwise
# (a worktree of either repo is not next to the other).
set -eu

root=$(git rev-parse --show-toplevel)
web=${OEEE_CAFE_WEB:-$root/../oeee-cafe-web}
source=$web/frontend/shared/appContract.json
target=$root/app/src/test/resources/appContract.json

if [ ! -f "$source" ]; then
    echo "No $source; set OEEE_CAFE_WEB to a checkout of oeee-cafe/web." >&2
    exit 1
fi

cp "$source" "$target"
echo "Copied $source to $target"
