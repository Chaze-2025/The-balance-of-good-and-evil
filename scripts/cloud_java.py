#!/usr/bin/env python3
"""Configure Java for the inherited cloud proxy without weakening TLS checks."""
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import subprocess
from urllib.parse import urlsplit

java = Path(os.environ['JAVA_HOME'])
cache = Path(os.environ['BALANCE_TOOLS_DIR'])
options = []
for scheme in ('http', 'https'):
    value = os.environ.get(scheme.upper() + '_PROXY')
    if value:
        proxy = urlsplit(value)
        if not proxy.hostname or proxy.username or proxy.password:
            raise SystemExit('Unsupported proxy URL: configure a host/port proxy without embedded credentials.')
        options.extend([f'-D{scheme}.proxyHost={proxy.hostname}',
                        f'-D{scheme}.proxyPort={proxy.port or 80}'])
options.append('-Dhttp.nonProxyHosts=localhost|127.*|[::1]')
cert = os.environ.get('CODEX_PROXY_CERT')
if cert:
    ca = Path(cert)
    if not ca.is_file():
        raise SystemExit('CODEX_PROXY_CERT does not point to a readable CA certificate.')
    digest = hashlib.sha256(ca.read_bytes()).hexdigest()
    trust = cache / ('trust-' + digest[:16] + '.p12')
    if not trust.exists():
        temporary = trust.with_suffix('.tmp')
        shutil.copy2(java / 'lib/security/cacerts', temporary)
        try:
            subprocess.run([str(java / 'bin/keytool'), '-importcert', '-noprompt',
                            '-alias', 'codex-session-proxy', '-file', str(ca),
                            '-keystore', str(temporary), '-storepass', 'changeit'],
                           check=True, capture_output=True)
            temporary.replace(trust)
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    options.extend([f'-Djavax.net.ssl.trustStore={trust}',
                    '-Djavax.net.ssl.trustStorePassword=changeit'])
# JAVA_TOOL_OPTIONS also reaches Gradle daemons and forked Minecraft processes.
current = os.environ.get('JAVA_TOOL_OPTIONS', '')
for option in options:
    if option not in current.split():
        current += ' ' + option
print('export JAVA_TOOL_OPTIONS=' + shlex.quote(current.strip()))
