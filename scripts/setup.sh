#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export BALANCE_TOOLS_DIR="${BALANCE_TOOLS_DIR:-/workspace/.tools/minecraft}"
command -v python3 >/dev/null
command -v curl >/dev/null
command -v git >/dev/null
for balance_tool in ffmpeg ffprobe; do
    if ! command -v "$balance_tool" >/dev/null; then
        printf 'Missing %s; install the free ffmpeg package from the OS repository before setup.\n' "$balance_tool" >&2
        exit 1
    fi
done
python3 -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ is required for setup"'
mkdir -p "$BALANCE_TOOLS_DIR/downloads"
python3 - <<'PY'
import hashlib
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile

base = Path(os.environ['BALANCE_TOOLS_DIR'])
version = '21.0.12.1+1'
jdk = base / ('jdk-' + version)
if not (jdk / 'bin/javac').is_file():
    if jdk.exists():
        raise SystemExit(f'Incomplete JDK at {jdk}; inspect it before replacing it.')
    archive = base / 'downloads/jdk21.tar.gz'
    sha = 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94'
    url = ('https://github.com/adoptium/temurin21-binaries/releases/download/'
           'jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz')
    if os.uname().machine != 'x86_64':
        raise SystemExit('This pinned cloud setup targets Linux x86_64; choose and verify the appropriate JDK for other architectures.')
    if not archive.is_file():
        part = archive.with_suffix('.part')
        subprocess.run(['curl', '-fLsS', '--retry', '2', '--max-time', '300', url, '-o', str(part)], check=True)
        part.replace(archive)
    if hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest() != sha:
        raise SystemExit('JDK SHA-256 mismatch; artifact not installed. Remove the corrupt download and investigate.')
    with tempfile.TemporaryDirectory(dir=base) as temporary:
        with tarfile.open(archive) as tar:
            tar.extractall(temporary, filter='data')
        (Path(temporary) / jdk.name).rename(jdk)
    print('Installed verified Eclipse Temurin JDK', version)
else:
    print('Reusing installed Eclipse Temurin JDK', version)
PY
source scripts/env.sh
java -version
javac -version
python3 - <<'PY'
import hashlib
from pathlib import Path
p = Path('gradle/wrapper/gradle-wrapper.jar')
assert hashlib.sha256(p.read_bytes()).hexdigest() == '7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172', 'Gradle wrapper checksum mismatch'
print('Gradle wrapper checksum verified')
PY
./gradlew --no-daemon --console=plain --version
./gradlew --no-daemon --console=plain build
