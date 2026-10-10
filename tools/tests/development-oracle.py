#!/usr/bin/env python3
"""Development Oracle preflight. No DDL/DML, stored credentials or provider calls.

Keep raw subprocess output, exception text and configuration out of diagnostics:
Oracle errors can include a connect descriptor, username or other private values.
This launcher deliberately does not repurpose the destructive Docker test runner.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import getpass
import html
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import struct
import subprocess
import sys
import tempfile
import warnings
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = {
    "format_version": 1,
    "environment": "development",
    "jdbc_url": "jdbc:oracle:thin:@//CHANGE_ME:1521/CHANGE_ME",
    "expected_db_unique_name": "CHANGE_ME",
    "expected_service_name": "CHANGE_ME",
    "expected_con_name": "CHANGE_ME",
    "expected_db_major_version": 19,
    "cert_table": "CHANGE_ME",
    "info_table": "CHANGE_ME",
}
IDENTITY = re.compile(r"[A-Za-z0-9_.$#-]{1,128}\Z")
TABLE = re.compile(r"[A-Z][A-Z0-9_]{0,29}\Z")
URL = re.compile(r"jdbc:oracle:thin:@//([A-Za-z0-9.-]{1,253}):([0-9]{1,5})/([A-Za-z0-9][A-Za-z0-9_.-]{0,127})\Z")
HOST_LABEL = re.compile(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\Z")
SAFE_CODE = re.compile(r"[A-Za-z0-9_.-]{1,80}\Z")
COMPLETED_CHECKS = [
    'connectionWithRequiredEncryptionAndIntegrity', 'readOnlyTransaction',
    'databaseIdentity', 'databaseMajorVersion', 'certificateColumns',
    'certificateInfoColumns', 'departmentColumns', 'sequenceMetadataOnly', 'databaseCharsets',
]


class SafeFailure(Exception):
    """Only fixed, non-sensitive diagnostic text belongs in this exception."""


class SafeArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        # Unknown arguments may be accidental --password values. Never echo them.
        self.print_usage(sys.stderr)
        self.exit(2, 'Invalid arguments. Use --help; credentials are accepted only at the hidden prompts.\n')


def external_path(value):
    # Never put even the non-password DB inventory in this checkout.
    path = Path(value).expanduser().absolute()
    if path.is_symlink():
        raise SafeFailure("Configuration symlinks are not accepted.")
    resolved = path.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise SafeFailure("Keep the private configuration outside the repository.")
    return path


def private_parent(path):
    info = path.parent.stat()
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid()
            or stat.S_IMODE(info.st_mode) != 0o700):
        raise SafeFailure("The configuration directory must be owned by you with mode 0700.")


def init_config(value):
    path = external_path(value)
    # The caller creates the private directory; do not chmod a shared directory.
    private_parent(path)
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    except FileExistsError as exc:
        raise SafeFailure("Configuration already exists; it was not overwritten.") from exc
    with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
        json.dump(TEMPLATE, output, ensure_ascii=False, indent=2)
        output.write('\n')
    print("Private configuration template created. Fill placeholders locally; do not add credentials.")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SafeFailure("Duplicate configuration keys are not accepted.")
        result[key] = value
    return result


def validate_config(config):
    if not isinstance(config, dict) or set(config) != set(TEMPLATE):
        raise SafeFailure("Configuration fields must match the generated template; credentials are not allowed.")
    if (type(config['format_version']) is not int or config['format_version'] != 1
            or config['environment'] != 'development'):
        raise SafeFailure("Only the development configuration format is accepted.")
    if type(config['expected_db_major_version']) is not int or config['expected_db_major_version'] != 19:
        raise SafeFailure("This development gate requires the expected Oracle major version 19.")
    for key, value in config.items():
        if key in ('format_version', 'expected_db_major_version'):
            continue
        if not isinstance(value, str) or not value or 'CHANGE_ME' in value:
            raise SafeFailure("Fill every placeholder with the approved development settings.")
    match = URL.fullmatch(config['jdbc_url'])
    if not match or not 1 <= int(match.group(2)) <= 65535:
        raise SafeFailure("Use a simple Oracle Thin host:port/service URL without credentials or URL options.")
    if any(not HOST_LABEL.fullmatch(label) for label in match.group(1).split('.')):
        raise SafeFailure("Use a valid hostname without empty labels or a trailing dot.")
    for key in ('expected_db_unique_name', 'expected_service_name', 'expected_con_name'):
        if not IDENTITY.fullmatch(config[key]):
            raise SafeFailure("Invalid expected database identity format.")
    if match.group(3).casefold() != config['expected_service_name'].casefold():
        raise SafeFailure("The URL service and expected service must agree.")
    if any(not TABLE.fullmatch(config[key]) for key in ('cert_table', 'info_table')):
        raise SafeFailure("Use uppercase, unqualified table identifiers of at most 30 characters.")
    return config


def read_config(value):
    path = external_path(value)
    private_parent(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, 'r', encoding='utf-8') as source:
        info = os.fstat(source.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
                or stat.S_IMODE(info.st_mode) != 0o600 or info.st_nlink != 1
                or info.st_size > 8192):
            raise SafeFailure("Configuration must be your regular, single-link 0600 file, at most 8 KiB.")
        try:
            config = json.load(source, object_pairs_hook=unique_object)
        except json.JSONDecodeError as exc:
            raise SafeFailure("Invalid JSON syntax; configuration values were not displayed.") from exc
        return validate_config(config)


def child_environment():
    # No password env, JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS, CLASSPATH or TNS_ADMIN.
    return {'PATH': os.environ.get('PATH', '/usr/bin:/bin'), 'LANG': 'C', 'LC_ALL': 'C'}


def bounded_run(command, *, data=None, timeout=45):
    process = subprocess.Popen(command, cwd=ROOT, env=child_environment(),
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, start_new_session=True)
    try:
        output, _ = process.communicate(data, timeout=timeout)
        return process.returncode, output
    except BaseException:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate()
        raise


def java_tools():
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
    if not java or not javac:
        raise SafeFailure("JDK 21 java and javac are required.")
    code, output = bounded_run([javac, '--version'])
    if code or not re.fullmatch(rb'javac 21(?:\.[^\r\n ]+)?\s*', output):
        raise SafeFailure("Use JDK 21 for the development probe.")
    # --version, unlike -version, emits version information to stdout.
    code, output = bounded_run([java, '--version'])
    if code or not re.match(rb'(?:openjdk|java) 21(?:[. +\r\n])', output):
        raise SafeFailure("Use JDK 21 for both java and javac.")
    return java, javac


def secret_input(prompt):
    # getpass normally falls back to echoing stdin when no TTY exists. Fail instead.
    with warnings.catch_warnings():
        warnings.simplefilter('error', getpass.GetPassWarning)
        value = getpass.getpass(prompt)
    if not value or len(value.encode('utf-8')) > 4096 or any(x in value for x in ('\x00', '\r', '\n')):
        raise SafeFailure("Input must be nonempty, single-line and at most 4096 bytes.")
    return value


def payload(config, user, password):
    # A binary, length-prefixed pipe avoids quoting, property expansion and escapes.
    values = (config['jdbc_url'], user, password, config['expected_db_unique_name'],
              config['expected_service_name'], config['expected_con_name'],
              config['cert_table'], config['info_table'], str(config['expected_db_major_version']))
    chunks = [value.encode('utf-8') for value in values]
    return b''.join(struct.pack('>I', len(chunk)) + chunk for chunk in chunks)


def safe_result(raw, exit_code):
    result = json.loads(raw)
    allowed = {'status', 'checks', 'failureCode', 'sqlState', 'errorCode',
               'dbMajorVersion', 'driverMajorVersion', 'charset', 'ncharset'}
    if not isinstance(result, dict) or not set(result) <= allowed:
        raise SafeFailure("Probe output did not match the safe report format.")
    if result.get('status') not in ('PASS', 'FAIL'):
        raise SafeFailure("Probe returned an invalid status.")
    if exit_code not in (0, 1, 2) or ((exit_code == 0) != (result['status'] == 'PASS')):
        raise SafeFailure("Probe exit status and report disagree.")
    for key, value in result.items():
        if key == 'checks':
            if not isinstance(value, list) or len(value) > 20 or any(
                    not isinstance(item, str) or not SAFE_CODE.fullmatch(item) for item in value):
                raise SafeFailure("Probe checks did not match the safe report format.")
        elif key in ('errorCode', 'dbMajorVersion', 'driverMajorVersion'):
            if type(value) is not int:
                raise SafeFailure("Probe numeric fields did not match the safe report format.")
        elif not isinstance(value, str) or not SAFE_CODE.fullmatch(value):
            raise SafeFailure("Probe fields did not match the safe report format.")
    if result['status'] == 'PASS':
        required = {'status', 'checks', 'dbMajorVersion', 'driverMajorVersion', 'charset', 'ncharset'}
        if (set(result) != required or result['checks'] != COMPLETED_CHECKS
                or result['dbMajorVersion'] != 19 or result['driverMajorVersion'] < 1):
            raise SafeFailure("Incomplete or inconsistent PASS report; no successful gate was recorded.")
    elif not {'failureCode', 'sqlState', 'errorCode'} <= set(result):
        raise SafeFailure("A failed probe must identify its failed check safely.")
    return result


def write_report(result):
    reports = ROOT / 'target/test-runs'
    reports.mkdir(parents=True, exist_ok=True)
    # Unique directory prevents old PASS results looking like the latest failed run.
    directory = Path(tempfile.mkdtemp(prefix='dev-oracle-', dir=reports))
    report = dict(result, scope='READ_ONLY_DEVELOPMENT_PREFLIGHT',
                  timestamp=datetime.now(timezone.utc).isoformat())
    def private_write(name, content):
        descriptor = os.open(directory / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
            output.write(content)
    private_write('summary.json', json.dumps(report, indent=2) + '\n')
    display = html.escape(json.dumps(report, indent=2, ensure_ascii=False))
    private_write('index.html',
        '<!doctype html><html lang="ko"><meta charset="utf-8">'
        '<title>Development Oracle preflight</title><h1>Development Oracle preflight</h1>'
        '<p>읽기 전용 접속·객체 점검입니다. 인증·쓰기·성능 테스트 결과가 아닙니다.</p>'
        '<pre>' + display + '</pre></html>\n')
    print('Report: ' + str(directory.relative_to(ROOT) / 'index.html'))


@contextmanager
def probe_lock():
    target = ROOT / 'target'
    target.mkdir(exist_ok=True)
    lock = target / '.mobilecert-test-lock'
    try:
        lock.mkdir()
    except FileExistsError as exc:
        raise SafeFailure("Another test may be running; do not run probes or Maven concurrently.") from exc
    try:
        yield
    finally:
        lock.rmdir()


def preflight(config):
    java, javac = java_tools()
    version = ET.parse(ROOT / 'pom.xml').findtext(
        '{http://maven.apache.org/POM/4.0.0}properties/{http://maven.apache.org/POM/4.0.0}ojdbc.version')
    if not version or not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', version):
        raise SafeFailure("The current POM must specify the Oracle driver version.")
    # Select this build's declared artifact, never an arbitrary leftover jar.
    driver = ROOT / 'target/lib' / ('ojdbc11-' + version + '.jar')
    if not driver.is_file():
        raise SafeFailure("Build with JDK 21: mvn -DskipTests package (the current POM's ojdbc11 jar is required).")
    with probe_lock(), tempfile.TemporaryDirectory(prefix='mobilecert-probe-') as temp:
        source = ROOT / 'tools/tests/java/DevelopmentOracleProbe.java'
        code, _ = bounded_run([javac, '--release', '21', '-encoding', 'UTF-8', '-d', temp, str(source)])
        if code:
            raise SafeFailure("The read-only probe did not compile; no database connection was attempted.")
        user = secret_input('Development DB account (hidden): ')
        password = secret_input('Development DB password (hidden): ')
        data = payload(config, user, password)
        try:
            code, output = bounded_run([java, '-XX:-HeapDumpOnOutOfMemoryError', '-cp',
                                       temp + os.pathsep + str(driver), 'DevelopmentOracleProbe'], data=data)
        except subprocess.TimeoutExpired:
            result = {'status': 'FAIL', 'failureCode': 'PROBE_TIMEOUT', 'sqlState': 'UNKNOWN', 'errorCode': 0}
            code = 1
        else:
            result = safe_result(output, code)
        finally:
            # Reduces lifetime; Python/JVM cannot guarantee all memory copies are erased.
            user = password = data = None
        write_report(result)
        print('Development Oracle read-only preflight: ' + result['status'])
        return code


def main():
    os.umask(0o077)
    def interrupted(signum, frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGHUP, interrupted)
    parser = SafeArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('init-config', 'check-config', 'preflight'))
    parser.add_argument('--config', required=True,
                        help='Private JSON path outside the repository; no account/password fields.')
    args = parser.parse_args()
    try:
        if args.mode == 'init-config':
            init_config(args.config)
            return 0
        config = read_config(args.config)
        if args.mode == 'check-config':
            print('Configuration format/permissions: PASS. No DB connection or credentials used.')
            return 0
        return preflight(config)
    except SafeFailure as exc:
        print(str(exc), file=sys.stderr)
    except (KeyboardInterrupt, EOFError, getpass.GetPassWarning):
        print('Cancelled or no secure terminal available; input was not echoed.', file=sys.stderr)
    except Exception:
        # Do not print path/JSON/OS/JDBC exception text: it may contain private data.
        print('Preflight could not complete; raw diagnostic output was withheld.', file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main())
