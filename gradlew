#!/bin/bash
# Simple wrapper that delegates to system gradle
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$DIR/.gradle}"
exec gradle "-g$GRADLE_USER_HOME" "$@"
