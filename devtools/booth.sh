#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in
    '') interactive=false ;;
    --interactive) interactive=true ;;
    *) echo 'Usage: booth.sh [--interactive]'; exit 2 ;;
esac
playtest_args=()
if [[ "${2:-}" == '--resume' && "$interactive" == true && $# == 2 ]]; then
    playtest_args+=('-PplaytestResume')
elif [[ "${2:-}" == '--reset' && "$interactive" == true && $# == 2 ]]; then
    playtest_args+=('-PplaytestReset')
elif [[ $# -gt 1 ]]; then
    echo 'Usage: booth.sh [--interactive [--resume|--reset]]'; exit 2
fi
mkdir -p run
# Run with host process visibility. Inspect before launch; never open a second rendering client.
uv run --no-project --python 3.14 python - <<'PY'
from pathlib import Path
for p in Path('/proc').iterdir():
    if not p.name.isdigit():
        continue
    try:
        if (p / 'comm').read_text().strip() != 'java':
            continue
        args = (p / 'cmdline').read_bytes()
        if b'GradleDaemon' not in args:
            raise SystemExit('Another Java process is present; identify it before launching the booth (PID ' + p.name + ').')
    except (OSError, PermissionError):
        pass
PY
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
if "$interactive"; then
    : "${DISPLAY:?Launch the interactive playtest from your desktop session}"
    # Interactive shaders need the desktop display and its native GPU driver.
    unset LIBGL_ALWAYS_SOFTWARE LIBGL_ALWAYS_INDIRECT GALLIUM_DRIVER
    unset MESA_GL_VERSION_OVERRIDE MESA_GLSL_VERSION_OVERRIDE MESA_LOADER_DRIVER_OVERRIDE
    unset __GLX_VENDOR_LIBRARY_NAME
    ./gradlew runPlaytest --offline "${playtest_args[@]}"
    exit
fi
existing="$(pgrep -a Xephyr || true)"
owned_pid=''
cleanup() {
    if [[ -n "$owned_pid" ]]; then kill "$owned_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT
if [[ -n "$existing" ]]; then
    display="$(printf '%s\n' "$existing" | awk '{for(i=2;i<=NF;i++) if($i ~ /^:[0-9]+$/) {print $i;exit}}')"
    if [[ -z "$display" ]]; then echo 'Cannot identify existing Xephyr display'; exit 1; fi
else
    display=''
    for number in {70..99}; do
        if [[ ! -S "/tmp/.X11-unix/X$number" && ! -e "/tmp/.X$number-lock" ]]; then display=":$number"; break; fi
    done
    if [[ -z "$display" ]]; then echo 'No free test display'; exit 1; fi
    Xephyr "$display" -screen 1280x720 -ac -br -noreset > run/xephyr.log 2>&1 &
    owned_pid=$!
    sleep 1
fi
export DISPLAY="$display"
export __GLX_VENDOR_LIBRARY_NAME=mesa LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460
timeout --kill-after=10s 300s ./gradlew runPhotoBooth --offline
