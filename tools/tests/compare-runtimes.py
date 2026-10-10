#!/usr/bin/env python3
"""Compare a pinned original Java 8 revision with current Java 8 and 21 sources.

Uses temporary directories, synthetic data, an in-memory JDBC driver, and a
non-network SocketManager. Never reads deployment configuration.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java8-home', required=True, type=Path)
parser.add_argument('--java21-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')))
parser.add_argument('--baseline-ref', default='c1b28bb96a2d7580c01fe6ad0e717a92e0cf721f')
parser.add_argument('--legacy-pool-jar', type=Path, help='Optional authentic jdbcpool 0.99 jar for original full compile and JDBC characterization')
parser.add_argument('--maven-repository', type=Path, default=Path.home() / '.m2/repository')
args = parser.parse_args()


def run(command):
    subprocess.run([str(item) for item in command], cwd=ROOT, check=True, timeout=60)


def compile_java(home, version, output, sources, classpath=None):
    output.mkdir()
    command = [home / 'bin/javac', '-encoding', 'EUC-KR', '-proc:none', '-d', output]
    command += ['-source', '1.8', '-target', '1.8'] if version == 8 else ['--release', '21']
    if classpath:
        command += ['-cp', classpath]
    run(command + sources)


with tempfile.TemporaryDirectory(prefix='mobilecert-compat-') as temporary:
    work = Path(temporary)
    original = work / 'original'
    paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', args.baseline_ref, 'src/main/java'], cwd=ROOT, text=True).splitlines()
    for name in paths:
        destination = original / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(subprocess.check_output(['git', 'show', f'{args.baseline_ref}:{name}'], cwd=ROOT))

    expected = None
    for label, source_root, home, version, charsets in [
        ('original', original, args.java8_home, 8, ['EUC-KR']),
        ('candidate-java8', ROOT, args.java8_home, 8, ['EUC-KR', 'UTF-8']),
        # Keep legacy bytes as the baseline; the candidate uses JDK 21's UTF-8 default.
        ('candidate-java21', ROOT, args.java21_home, 21, ['UTF-8']),
    ]:
        source = source_root / 'src/main/java/scbk/cert'
        common = [source / 'util/CommonUtil.java'] + sorted((source / 'bean').rglob('*.java'))
        if (source / 'util/CertCharset.java').exists():
            common.append(source / 'util/CertCharset.java')
        log = ROOT / 'tools/tests/stubs/scbk/cert/util/Log.java'
        protocol = work / (label + '-protocol')
        compile_java(home, version, protocol, common + [log, source / 'server/SocketManager.java', ROOT / 'src/test/java/scbk/cert/baseline/ProtocolBaseline.java'])
        provider = work / (label + '-provider')
        compile_java(home, version, provider, common + [
            log, source / 'conf/ConfigBean.java', source / 'conf/KCBConfigBean.java',
            source / 'main/Communication.java', source / 'main/KCBCommunication.java',
            ROOT / 'tools/tests/provider-stubs/scbk/cert/server/SocketManager.java',
            ROOT / 'tools/tests/ProviderBaseline.java'])
        for charset in charsets:
            run([home / 'bin/java', '-Dfile.encoding=' + charset, '-Dscbk.cert.test.defaultCharset=' + charset,
                 '-cp', protocol, 'scbk.cert.baseline.ProtocolBaseline'])
            snapshot = work / (label + '-' + charset + '.bin')
            run([home / 'bin/java', '-Dfile.encoding=' + charset, '-cp', provider,
                 'scbk.cert.baseline.ProviderBaseline', snapshot])
            actual = snapshot.read_bytes()
            if expected is None:
                expected = actual
            if actual != expected:
                raise SystemExit(f'FAIL provider bytes differ: {label} {charset}')
            print(f'PASS exact provider bytes: {label} {charset}; sha256={hashlib.sha256(actual).hexdigest()}', flush=True)

    if args.legacy_pool_jar:
        if not args.legacy_pool_jar.is_file():
            raise SystemExit('Legacy pool jar is missing')
        libraries = [args.legacy_pool_jar.resolve()]
        for artifact in ['log4j-api', 'log4j-core', 'log4j-1.2-api']:
            jar = args.maven_repository / f'org/apache/logging/log4j/{artifact}/2.17.1/{artifact}-2.17.1.jar'
            if not jar.is_file():
                raise SystemExit('Missing original-version dependency: ' + str(jar))
            libraries.append(jar)
        classpath = os.pathsep.join(map(str, libraries))
        full = work / 'original-full'
        compile_java(args.java8_home, 8, full, sorted((original / 'src/main/java').rglob('*.java')), classpath)
        tests = work / 'original-jdbc-test'
        compile_java(args.java8_home, 8, tests, [
            ROOT / 'tools/tests/stubs/scbk/cert/util/Log.java',
            ROOT / 'src/test/java/scbk/cert/dao/RecordingDriver.java',
            ROOT / 'src/test/java/scbk/cert/dao/LegacyJdbcBaseline.java'], str(full) + os.pathsep + classpath)
        run([args.java8_home / 'bin/java', '-Dfile.encoding=EUC-KR', '-cp',
             os.pathsep.join([str(tests), str(full), classpath]), 'scbk.cert.dao.LegacyJdbcBaseline'])
        print('PASS original full Java 8 compile and legacy JDBC characterization', flush=True)
    else:
        print('SKIP original full build/JDBC characterization: supply --legacy-pool-jar', flush=True)
