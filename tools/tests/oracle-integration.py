#!/usr/bin/env python3
"""Provision synthetic SMS objects in a disposable local Docker Oracle PDB."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / 'target'
STATE_FILE = TARGET / 'oracle-test-state.json'
LOCK = TARGET / '.mobilecert-test-lock'
MARKER = 'MOBILECERT_ORACLE_IT_V1'
PDB_PATTERN = re.compile(r'MCERTIT[A-F0-9]{8}\Z')


class VerificationError(Exception):
    pass


def run(command, *, stdin=None, env=None, timeout=30):
    process = subprocess.Popen(command, stdin=subprocess.PIPE, text=True, encoding='utf-8',
                               errors='replace', stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               cwd=ROOT, env=env, start_new_session=True)
    try:
        output, _ = process.communicate(stdin, timeout=timeout)
        return subprocess.CompletedProcess(command, process.returncode, stdout=output)
    except subprocess.TimeoutExpired as exc:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise VerificationError('A bounded command timed out; captured output was withheld.') from exc
    except BaseException:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise


def require_success(result, action):
    if result.returncode:
        raise VerificationError(action + ' failed; raw output was withheld to protect credentials.')
    return result.stdout


class LocalOracle:
    def __init__(self, container, port):
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', container):
            raise VerificationError('Invalid local container name.')
        if not 1 <= port <= 65535:
            raise VerificationError('Invalid local listener port.')
        configured_host = os.environ.get('DOCKER_HOST', '')
        if configured_host and not configured_host.startswith('unix://'):
            raise VerificationError('Only a local Docker unix socket is allowed.')
        context = os.environ.get('DOCKER_CONTEXT')
        args = ['docker', 'context', 'inspect']
        if context:
            args.append(context)
        args += ['--format', '{{.Endpoints.docker.Host}}']
        context_host = require_success(run(args), 'Docker context inspection').strip()
        if not context_host.startswith('unix://'):
            raise VerificationError('The Docker context must use a local unix socket.')
        endpoint = context_host if context else configured_host or context_host
        self.docker = ['docker', '--host', endpoint]
        self.container = container
        self.port = port
        value = require_success(run(self.docker + ['inspect', '--format',
                                '{{.Id}} {{.State.Running}}', container]),
                                'Local container inspection').strip().split()
        if len(value) != 2 or not re.fullmatch(r'[a-f0-9]{64}', value[0]):
            raise VerificationError('The local container identity could not be verified.')
        if value[1] != 'true':
            raise VerificationError('The local Oracle container must already be running.')
        self.container_id = value[0]
        ports = json.loads(require_success(run(self.docker + ['inspect', '--format',
                           '{{json .NetworkSettings.Ports}}', self.container_id]),
                           'Local listener inspection'))
        bindings = ports.get('1521/tcp') or []
        if not any(binding.get('HostPort') == str(port) and
                   binding.get('HostIp') in ('127.0.0.1', '0.0.0.0', '::')
                   for binding in bindings):
            raise VerificationError('The selected Oracle listener is not published on loopback.')

    def sql(self, body, *, timeout=30):
        script = ('WHENEVER OSERROR EXIT FAILURE ROLLBACK\n'
                  'WHENEVER SQLERROR EXIT FAILURE ROLLBACK\n'
                  'SET ECHO OFF VERIFY OFF FEEDBACK OFF HEADING OFF PAGESIZE 0\n'
                  'SET DEFINE OFF LINESIZE 32767 TRIMSPOOL ON\n' + body + '\nEXIT\n')
        result = run(self.docker + ['exec', '-i', self.container_id,
                                   'sqlplus', '-s', '/', 'as', 'sysdba'],
                     stdin=script, timeout=timeout)
        errors = sorted(set(re.findall(r'\b(?:ORA|SP2)-[0-9]+\b', result.stdout)))
        if result.returncode or errors:
            suffix = ' (' + ', '.join(errors) + ')' if errors else ''
            raise VerificationError('Local Oracle SQL failed' + suffix + '; SQL output was withheld.')
        return result.stdout.strip()

    def wait_ready(self):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                output = self.sql("SELECT 'MCERT_READY' FROM V$INSTANCE WHERE STATUS='OPEN';", timeout=10)
                if 'MCERT_READY' in output.splitlines():
                    return
            except VerificationError:
                pass
            time.sleep(min(5, max(0, deadline - time.monotonic())))
        raise VerificationError('Local Oracle was not ready within 180 seconds.')

    def verify_state(self, state):
        if (state.get('format') != 1 or state.get('container_id') != self.container_id
                or state.get('container') != self.container or state.get('user') != 'SMS'
                or state.get('marker') != MARKER or not PDB_PATTERN.fullmatch(state.get('pdb', ''))
                or state.get('url') != f"jdbc:oracle:thin:@//127.0.0.1:{self.port}/{state.get('pdb')}"
                or not re.fullmatch(r'[A-Za-z0-9]{24,80}', state.get('password', ''))):
            raise VerificationError('Recorded test state does not match this local container and synthetic PDB.')
        output = self.sql(f"ALTER SESSION SET CONTAINER={state['pdb']};\n"
                          "SELECT MARKER_VALUE FROM SMS.MCERT_IT_MARKER "
                          "WHERE MARKER_KEY='SYNTHETIC_SCHEMA';")
        if MARKER not in output.splitlines():
            raise VerificationError('Synthetic schema ownership marker was not verified.')

    def wait_listener(self, state):
        # Use SQLPlus /nolog so generated credentials only travel through stdin.
        deadline = time.monotonic() + 180
        script = ('WHENEVER OSERROR EXIT FAILURE\nWHENEVER SQLERROR EXIT FAILURE\n'
                  'SET ECHO OFF VERIFY OFF FEEDBACK OFF HEADING OFF PAGESIZE 0\n'
                  f'CONNECT SMS/"{state["password"]}"@//127.0.0.1:1521/{state["pdb"]}\n'
                  "SELECT 'MCERT_LISTENER_READY' FROM DUAL;\nEXIT\n")
        while time.monotonic() < deadline:
            try:
                result = run(self.docker + ['exec', '-i', self.container_id, 'sqlplus', '-s', '/nolog'],
                             stdin=script, timeout=10)
                if (result.returncode == 0 and 'MCERT_LISTENER_READY' in result.stdout.splitlines()
                        and not re.search(r'\b(?:ORA|SP2)-[0-9]+\b', result.stdout)):
                    return
            except VerificationError:
                pass
            time.sleep(min(5, max(0, deadline - time.monotonic())))
        raise VerificationError('Disposable PDB listener login was not ready within 180 seconds.')

    def save_metadata(self, state):
        metadata = self.sql(f'ALTER SESSION SET CONTAINER={state["pdb"]};\n'
                            "SELECT 'ORACLE_VERSION=' || VERSION_FULL FROM V$INSTANCE;\n"
                            "SELECT PARAMETER || '=' || VALUE FROM NLS_DATABASE_PARAMETERS "
                            "WHERE PARAMETER IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET') ORDER BY PARAMETER;")
        (TARGET / 'oracle-environment.txt').write_text(metadata + '\n', encoding='utf-8')

    def drop(self, pdb):
        if not PDB_PATTERN.fullmatch(pdb):
            raise VerificationError('Refusing to clean an unrecognized PDB name.')
        # Only the recorded, verified test PDB (or one created in this process) is eligible.
        output = self.sql(f"SELECT OPEN_MODE FROM V$PDBS WHERE NAME='{pdb}';")
        if not output:
            return
        if 'MOUNTED' not in output.splitlines():
            self.sql(f'ALTER PLUGGABLE DATABASE {pdb} CLOSE IMMEDIATE;', timeout=90)
        self.sql(f'DROP PLUGGABLE DATABASE {pdb} INCLUDING DATAFILES;', timeout=180)


def write_state(state, *, exclusive=False):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if exclusive else os.O_TRUNC)
    descriptor = os.open(STATE_FILE, flags, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as handle:
        os.fchmod(handle.fileno(), 0o600)
        json.dump(state, handle, indent=2)
        handle.write('\n')


def read_state():
    info = STATE_FILE.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_mode & 0o077:
        raise VerificationError('The Oracle test state must be a regular file with mode 0600.')
    state = json.loads(STATE_FILE.read_text(encoding='utf-8'))
    if not isinstance(state, dict):
        raise VerificationError('Invalid Oracle test state format.')
    return state


def provision(oracle, state):
    pdb = state['pdb']
    admin_password = 'A' + secrets.token_hex(20)
    oracle.sql(f'CREATE PLUGGABLE DATABASE {pdb} ADMIN USER MCERT_ADMIN '
               f'IDENTIFIED BY "{admin_password}" CREATE_FILE_DEST=\'/opt/oracle/oradata\';', timeout=180)
    oracle.sql(f'ALTER PLUGGABLE DATABASE {pdb} OPEN;\nALTER SYSTEM REGISTER;', timeout=90)
    oracle.sql(f'ALTER SESSION SET CONTAINER={pdb};\n'
               "CREATE TABLESPACE MCERT_IT DATAFILE SIZE 20M AUTOEXTEND ON NEXT 10M MAXSIZE 100M;\n"
               f'CREATE USER SMS IDENTIFIED BY "{state["password"]}" DEFAULT TABLESPACE MCERT_IT '
               'QUOTA 100M ON MCERT_IT;\nGRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO SMS;\n'
               'ALTER SESSION SET CURRENT_SCHEMA=SMS;\n' +
               (ROOT / 'src/test/resources/oracle/schema.sql').read_text(encoding='utf-8'), timeout=90)
    oracle.verify_state(state)


def scrub(text, state):
    return text.replace(state['password'], '[REDACTED]')


def test_modes(state, selected, maven_version):
    env = os.environ.copy()
    env.update({'MOBILECERT_ORACLE_URL': state['url'], 'MOBILECERT_ORACLE_USER': state['user'],
                'MOBILECERT_ORACLE_PASSWORD': state['password'], 'MOBILECERT_ORACLE_PDB': state['pdb']})
    modes = ['oracle', 'oracle-readiness'] if selected == 'all' else [selected]
    overall = 0
    for mode in modes:
        report = TARGET / 'test-runs' / mode
        report.mkdir(parents=True, exist_ok=True)
        for parent in (TARGET / 'surefire-reports', report):
            for path in parent.glob('TEST-*.xml'):
                path.unlink()
        metadata_path = TARGET / 'oracle-environment.txt'
        metadata = metadata_path.read_text(encoding='utf-8') if metadata_path.exists() else ''
        (report / 'environment.txt').write_text(scrub(maven_version + '\n' + metadata, state), encoding='utf-8')
        status = 1
        try:
            result = run(['mvn', '-B', '-ntp', '-P' + mode, 'test'], env=env, timeout=600)
            (report / 'maven.log').write_text(scrub(result.stdout, state), encoding='utf-8')
            status = 0 if result.returncode == 0 else 1
        except VerificationError as exc:
            (report / 'maven.log').write_text(str(exc) + '\n', encoding='utf-8')
        # Credentials are environment variables, but defensively redact generated XML/logs too.
        for path in (TARGET / 'surefire-reports').glob('*'):
            if path.is_file() and path.suffix in ('.xml', '.txt'):
                content = path.read_text(encoding='utf-8', errors='replace')
                cleaned = scrub(content, state)
                if cleaned != content:
                    path.write_text(cleaned, encoding='utf-8')
        report_result = run([sys.executable, str(ROOT / 'tools/tests/test-report.py'), mode, str(status)], timeout=30)
        # The report generator prints aggregate counts only; never forward Maven/SQL output.
        for line in report_result.stdout.splitlines():
            if re.fullmatch(r"(?:oracle|oracle-readiness): (?:PASS|FAIL) \{'PASS': \d+, 'FAIL': \d+, 'SKIP': \d+\}", line):
                print(line, flush=True)
        if status or report_result.returncode:
            overall = 1
    return overall


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=(
        'Requires Python 3, Docker with a local unix socket, a running local Oracle container, '
        'JDK 21 and Maven. Reports: target/test-runs/<mode>/index.html. '
        'Synthetic tables only; no production configuration or provider calls. '
        'Default removes a newly created PDB even when tests fail. '
        'Existing retained state is verified and reused without automatic deletion. '
        'Exit: 0 passed, 1 tests/build failed, 2 environment/provision/cleanup failed.'))
    parser.add_argument('--container', default='sms-oracle', help='existing running local Oracle container')
    parser.add_argument('--port', type=int, default=1521, help='published localhost Oracle listener port')
    parser.add_argument('--mode', choices=('all', 'oracle', 'oracle-readiness'), default='all')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--keep', action='store_true', help='retain newly created test PDB and private state for inspection')
    group.add_argument('--cleanup', action='store_true', help='drop only the recorded, marked synthetic test PDB')
    args = parser.parse_args()
    for command in ('docker',) if args.cleanup else ('docker', 'mvn'):
        if not shutil.which(command):
            raise VerificationError('Missing required command: ' + command)
    maven_version = ''
    if not args.cleanup:
        maven_version = require_success(run(['mvn', '-version']), 'Maven version check')
        if not re.search(r'Java version: 21(?:[.,\s])', maven_version):
            raise VerificationError('Maven must use JDK 21. Set JAVA_HOME and PATH.')
    TARGET.mkdir(exist_ok=True)
    try:
        LOCK.mkdir()
    except FileExistsError as exc:
        raise VerificationError('Another verification run may be active (target/.mobilecert-test-lock).') from exc
    oracle = None
    state = None
    created_here = False
    schema_ready = False
    try:
        oracle = LocalOracle(args.container, args.port)
        oracle.wait_ready()
        if STATE_FILE.exists() or STATE_FILE.is_symlink():
            state = read_state()
            oracle.verify_state(state)
            if args.cleanup:
                oracle.drop(state['pdb'])
                STATE_FILE.unlink()
                return 0
        elif args.cleanup:
            raise VerificationError('No recorded test state exists; no PDB was modified.')
        else:
            pdb = 'MCERTIT' + secrets.token_hex(4).upper()
            existing = oracle.sql(f"SELECT COUNT(*) FROM V$PDBS WHERE NAME='{pdb}';")
            if existing.strip() != '0':
                raise VerificationError('Generated test PDB name already exists; no objects were modified.')
            state = {'format': 1, 'container': args.container, 'container_id': oracle.container_id,
                     'pdb': pdb, 'user': 'SMS', 'password': 'T' + secrets.token_hex(20),
                     'url': f'jdbc:oracle:thin:@//127.0.0.1:{args.port}/{pdb}', 'marker': MARKER}
            write_state(state, exclusive=True)
            created_here = True
            provision(oracle, state)
        schema_ready = True
        oracle.wait_listener(state)
        oracle.save_metadata(state)
        return test_modes(state, args.mode, maven_version)
    finally:
        try:
            if created_here and (not args.keep or not schema_ready) and oracle is not None:
                oracle.drop(state['pdb'])
                STATE_FILE.unlink(missing_ok=True)
        finally:
            LOCK.rmdir()


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, lambda _signum, _frame: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print('Oracle verification interrupted; cleanup was attempted for any new disposable PDB.', file=sys.stderr)
        sys.exit(2)
    except (VerificationError, OSError, ValueError, TypeError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
