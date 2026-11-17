#!/usr/bin/env python3
"""Use the cloud's writable tool paths, trusted CA store, and configured proxy."""
import os
from pathlib import Path
import shlex
import sys
from urllib.parse import urlparse

root = Path(__file__).resolve().parent.parent
tools = Path(os.environ.get('SOCIALDARK_TOOLCHAINS', '/workspace/toolchains'))
settings = {
    'JAVA_HOME': tools / 'jdk-17',
    'ANDROID_HOME': tools / 'android-sdk',
    'ANDROID_SDK_ROOT': tools / 'android-sdk',
    'ANDROID_USER_HOME': tools / 'android-user',
    'ANDROID_AVD_HOME': tools / 'avd',
    'GRADLE_USER_HOME': tools / 'gradle-home',
}
for key, value in settings.items():
    os.environ[key] = str(value)
os.environ['PATH'] = str(settings['JAVA_HOME'] / 'bin') + os.pathsep + os.environ['PATH']
user_dir = tools / 'user-home'
user_dir.mkdir(parents=True, exist_ok=True)
options = ['-Duser.home=' + str(user_dir)]
trust = Path('/etc/ssl/certs/java/cacerts')
if trust.exists():
    options.append('-Djavax.net.ssl.trustStore=' + str(trust))
proxy = urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
if proxy.hostname:
    for scheme in ['https', 'http']:
        options.extend([f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}'])
    # Keep local emulator traffic local. Proxy credentials stay with the platform.
    options.append('-Dhttp.nonProxyHosts=localhost|127.*|[::1]')
os.environ['JAVA_TOOL_OPTIONS'] = (os.environ.get('JAVA_TOOL_OPTIONS', '') + ' ' + ' '.join(shlex.quote(v) for v in options)).strip()
os.chdir(root)
os.execv(str(root / 'gradlew'), [str(root / 'gradlew'), *sys.argv[1:]])
