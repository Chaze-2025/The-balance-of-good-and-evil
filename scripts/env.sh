# Source this file from the repository root: . scripts/env.sh
# Dependencies live outside the checkout; no tracked files are rewritten.
export BALANCE_TOOLS_DIR="${BALANCE_TOOLS_DIR:-/workspace/.tools/minecraft}"
export JAVA_HOME="$BALANCE_TOOLS_DIR/jdk-21.0.12.1+1"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.cache/gradle-balance}"
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
    printf '%s\n' 'Full JDK missing; run bash scripts/setup.sh first.' >&2
    return 1
fi
# Only CA certificates and proxy host/port are used, never credential values.
balance_java_options="$(python3 scripts/cloud_java.py)" || return 1
eval "$balance_java_options"
unset balance_java_options
