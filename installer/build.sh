#!/usr/bin/env bash
# Build the no-admin Orbital installer and stage its payload.
#
# Toolchain note: the project rule "always compile with MSVC cl.exe" could not
# be applied here because cl.exe is not installed on this machine. Péterson
# explicitly authorized MinGW g++/windres for this installer. See
# docs/INSTALADOR_SEM_ADMIN.md.
#
# Outputs (all under the Git-ignored build/ directory):
#   build/installer/OrbitalSetup.exe
#   build/installer/payload/          <- copy of deploy/windows64/Orbital
#   build/installer/app_res.o

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/installer/src"
DEPLOY="$ROOT/deploy"
OUT="$ROOT/build/installer"
APP_IMAGE="$ROOT/deploy/windows64/Orbital"
ICON="$ROOT/deploy/orbital.ico"

CXX="${CXX:-g++}"
WINDRES="${WINDRES:-windres}"

for tool in "$CXX" "$WINDRES"; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Ferramenta ausente: %s\n' "$tool" >&2
        exit 2
    fi
done

if [[ ! -f "$APP_IMAGE/Orbital.exe" ]]; then
    printf 'App-image ausente: %s\n' "$APP_IMAGE/Orbital.exe" >&2
    exit 2
fi

# deploy/ is git-ignored: on a fresh clone the icon only exists after running
# `java tools/ArtConverter.java` (see docs/BUILD_EXECUTAVEL.md, Passo 0).
if [[ ! -f "$ICON" ]]; then
    printf 'Icone ausente: %s\n' "$ICON" >&2
    printf 'Gere com: java tools/ArtConverter.java\n' >&2
    exit 3
fi

mkdir -p "$OUT"

# 1. Embed the asInvoker manifest + version resource + app icon.
# MinGW's startup spec pulls in "default-manifest.o" automatically. Naming our
# resource object exactly that, and putting its directory first in the -B
# startfile prefix, replaces the stock manifest instead of producing two
# conflicting manifests (".rsrc merge failure"). --include-dir also points at
# deploy/ so windres resolves the "orbital.ico" referenced by app.rc.
"$WINDRES" -i "$SRC/app.rc" -o "$OUT/default-manifest.o" -O coff \
    --include-dir "$SRC" --include-dir "$DEPLOY"

# 2. Build a statically linked, dependency-free Win32 GUI executable.
"$CXX" -municode -mwindows -std=c++17 -O2 -Wall -Wextra \
    -static -static-libgcc -static-libstdc++ \
    -B"$OUT/" -L"$OUT" "$SRC/main.cpp" \
    -o "$OUT/OrbitalSetup.exe" \
    -lcomctl32 -lshell32 -lole32 -luuid -ladvapi32 -luser32 -lgdi32 -lkernel32

# 3. Stage a clean payload copy. Only this generated path is removed.
if [[ "$OUT" != "$ROOT/build/installer" ]]; then
    printf 'Recusando limpeza fora de build/installer: %s\n' "$OUT" >&2
    exit 2
fi
python - "$OUT/payload" <<'PY'
import os, shutil, stat, sys
from pathlib import Path
p = Path(sys.argv[1])
if not p.exists():
    sys.exit(0)
def onerror(func, path, exc):
    # jpackage marks files AND directories in the app-image read-only, and
    # `cp -r` carries that over; stdlib refuses to delete them until the
    # attribute is cleared.
    try:
        os.chmod(path, stat.S_IWRITE)
        func(path)
    except Exception:
        raise
shutil.rmtree(p, onerror=onerror)
PY
mkdir -p "$OUT/payload"
cp -r "$APP_IMAGE/." "$OUT/payload/"

printf 'Instalador: %s\n' "$OUT/OrbitalSetup.exe"
printf 'Payload:    %s\n' "$OUT/payload"
python - "$OUT/payload" <<'PY'
import sys
from pathlib import Path
files = [f for f in Path(sys.argv[1]).rglob('*') if f.is_file()]
print(f'Arquivos no payload: {len(files)}')
print(f'Tamanho total: {sum(f.stat().st_size for f in files) / 1024 / 1024:.1f} MB')
PY
