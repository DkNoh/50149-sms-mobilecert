#!/usr/bin/env python3
"""Offline probe checks. Uses a temporary fake JDBC driver; never connects to a DB."""

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
from unittest.mock import patch
import zipfile


HERE = Path(__file__).resolve().parent
BASE_FIELDS = [
    "jdbc:oracle:thin:@//no-network.invalid:1521/TEST_SVC",
    "TEST_USER",
    "TEST_SECRET",
    "TEST_DB",
    "TEST_SVC",
    "TEST_PDB",
    "KCB_CERT202610",
    "KCB_INFO202610",
    "19",
]
ALLOWED_OUTPUT_KEYS = {
    "status", "checks", "failureCode", "sqlState", "errorCode",
    "dbMajorVersion", "driverMajorVersion", "charset", "ncharset",
}


class VerificationFailure(Exception):
    """Only fixed diagnostic codes are rendered; subprocess output stays private."""


def require(condition, code):
    if not condition:
        raise VerificationFailure(code)


def clean_environment():
    env = os.environ.copy()
    for name in (
        "CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS",
        "JDK_JAVAC_OPTIONS", "PROBE_STUB_MODE", "PROBE_STUB_AUDIT",
    ):
        env.pop(name, None)
    return env


def run(command, *, env, data=None, timeout=15):
    return subprocess.run(command, input=data, capture_output=True, env=env,
                          timeout=timeout, check=False)


def find_jdk21(env):
    candidates = []
    if env.get("JAVA_HOME"):
        home = Path(env["JAVA_HOME"])
        candidates.append((home / "bin/java", home / "bin/javac"))
    java_home = Path("/usr/libexec/java_home")
    if sys.platform == "darwin" and java_home.is_file():
        result = run([str(java_home), "-v", "21"], env=env, timeout=10)
        if result.returncode == 0:
            home = Path(result.stdout.decode("utf-8", errors="strict").strip())
            candidates.append((home / "bin/java", home / "bin/javac"))
    java, javac = shutil.which("java"), shutil.which("javac")
    if java and javac:
        candidates.append((Path(java), Path(javac)))
    for java, javac in candidates:
        if not java.is_file() or not javac.is_file():
            continue
        try:
            runtime = run([str(java), "-version"], env=env, timeout=10)
            compiler = run([str(javac), "-version"], env=env, timeout=10)
        except (OSError, subprocess.TimeoutExpired):
            continue
        runtime_version = (runtime.stdout + runtime.stderr).decode("utf-8", errors="replace")
        compiler_version = (compiler.stdout + compiler.stderr).decode("utf-8", errors="replace")
        if (runtime.returncode == 0 and compiler.returncode == 0
                and re.search(r'version "21(?:[.\-"]|$)', runtime_version)
                and re.search(r"javac 21(?:[.\s\-]|$)", compiler_version)):
            return str(java), str(javac)
    raise VerificationFailure("JDK_21_UNAVAILABLE")


def encode_fields(fields):
    return b"".join(struct.pack(">i", len(encoded)) + encoded
                    for field in fields for encoded in [field.encode("utf-8")])


def changed_field(index, value):
    fields = BASE_FIELDS.copy()
    fields[index] = value
    return encode_fields(fields)


def verify_output(result):
    require(result.stderr == b"", "STDERR_NOT_EMPTY")
    require(len(result.stdout.splitlines()) == 1, "RESULT_NOT_ONE_LINE")
    for sentinel in (b"SECRET", b"TEST_USER", b"TEST_DB", b"TEST_SVC", b"TEST_PDB",
                     b"KCB_CERT202610", b"KCB_INFO202610", b"no-network.invalid"):
        require(sentinel not in result.stdout and sentinel not in result.stderr, "PRIVATE_OUTPUT_LEAK")
    try:
        output = json.loads(result.stdout)
    except (ValueError, UnicodeError):
        raise VerificationFailure("INVALID_JSON_RESULT") from None
    require(isinstance(output, dict) and set(output) <= ALLOWED_OUTPUT_KEYS, "INVALID_RESULT_KEYS")
    return output


def verify_input_cases(java, classes, env):
    cases = [
        (b"", "TRUNCATED_INPUT"),
        (struct.pack(">i", -1), "INVALID_INPUT_LENGTH"),
        (struct.pack(">i", 4097), "INVALID_INPUT_LENGTH"),
        (struct.pack(">i", 1) + b"\xff", "INVALID_UTF8_INPUT"),
        (changed_field(2, " "), "INVALID_INPUT_FIELD"),
        (changed_field(0, "jdbc:oracle:thin:@(DESCRIPTION=secret)"), "INVALID_JDBC_URL"),
        (changed_field(0, BASE_FIELDS[0] + "?password=secret"), "INVALID_JDBC_URL"),
        (changed_field(0, "jdbc:oracle:thin:@//test:0/test"), "INVALID_JDBC_URL"),
        (changed_field(0, "jdbc:oracle:thin:@//test..host:1521/test"), "INVALID_JDBC_URL"),
        (changed_field(6, "X WHERE SECRET=1"), "INVALID_TABLE_IDENTIFIER"),
        (changed_field(8, "19.0"), "INVALID_EXPECTED_DB_VERSION"),
        (encode_fields(BASE_FIELDS) + b"X", "EXTRA_INPUT"),
        # No real JDBC jar is on this classpath. Valid input must stop before a connection.
        (encode_fields(BASE_FIELDS), "ORACLE_DRIVER_UNAVAILABLE"),
    ]
    for data, expected in cases:
        result = run([java, "-cp", str(classes), "DevelopmentOracleProbe"], env=env, data=data)
        output = verify_output(result)
        require(result.returncode == 2 and output.get("status") == "FAIL"
                and output.get("failureCode") == expected, "INPUT_CASE_FAILED")
    return len(cases)


def verify_sql_cases(java, classes, fake_classes, temporary, env):
    cases = {
        "success": None,
        "connection_failure": "CONNECTION_FAILED",
        "readonly_failure": "READ_ONLY_TRANSACTION_FAILED",
        "identity_mismatch": "DB_IDENTITY_MISMATCH",
        "version_mismatch": "DB_VERSION_MISMATCH",
        "columns_failure": "CERT_COLUMNS_CHECK_FAILED",
        "sequence_missing": "SEQUENCE_METADATA_NOT_VISIBLE",
        "invalid_charset": "INVALID_CHARSET_METADATA",
        "rollback_failure": "ROLLBACK_FAILED",
        "close_failure": "CONNECTION_CLOSE_FAILED",
    }
    classpath = str(classes) + os.pathsep + str(fake_classes)
    for mode, expected in cases.items():
        audit = temporary / (mode + ".audit")
        local_env = dict(env, PROBE_STUB_MODE=mode, PROBE_STUB_AUDIT=str(audit))
        result = run([java, "-cp", classpath, "DevelopmentOracleProbe"],
                     env=local_env, data=encode_fields(BASE_FIELDS))
        output = verify_output(result)
        require(audit.is_file(), "STUB_NOT_EXECUTED")
        events = audit.read_text(encoding="utf-8").splitlines()
        require(result.returncode == (0 if expected is None else 1), "SQL_CASE_EXIT_FAILED")
        require(output.get("status") == ("PASS" if expected is None else "FAIL"), "SQL_CASE_STATUS_FAILED")
        if expected is not None:
            require(output.get("failureCode") == expected, "SQL_CASE_CODE_FAILED")
        if mode != "connection_failure":
            require(events[-2:] == ["rollback", "connection_close"], "CLEANUP_NOT_GUARANTEED")
        if mode == "identity_mismatch":
            require("explicit_columns_no_rows" not in events and "sequence_metadata_only" not in events,
                    "IDENTITY_MISMATCH_DID_NOT_STOP")
        if mode == "columns_failure":
            require(output.get("sqlState") == "42000" and output.get("errorCode") == 942,
                    "SQL_DIAGNOSTICS_NOT_SANITIZED")
        if mode == "success":
            require(events.index("set_transaction_read_only_first") < events.index("identity_bound_check")
                    < events.index("explicit_columns_no_rows"), "READ_ONLY_ORDER_FAILED")
            require(events.count("explicit_columns_no_rows") == 3, "COLUMN_CHECK_COUNT_FAILED")
            require(events.count("sequence_metadata_only") == 1, "SEQUENCE_METADATA_CHECK_FAILED")
            require(output.get("dbMajorVersion") == 19 and output.get("driverMajorVersion") == 23
                    and output.get("charset") == "AL32UTF8" and output.get("ncharset") == "AL16UTF16",
                    "METADATA_RESULT_FAILED")
            require(len(output.get("checks", [])) == 9, "SUCCESS_CHECK_COUNT_FAILED")
    return len(cases)


def verify_launcher_cases(java, fake_classes, temporary, env):
    spec = importlib.util.spec_from_file_location("offline_development_oracle", HERE / "development-oracle.py")
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    # This entire pretend application tree is temporary. The repository's real
    # target/lib and its Oracle driver artifact are neither read nor modified.
    launcher_root = temporary / "pretend-application"
    library = launcher_root / "target/lib"
    library.mkdir(parents=True)
    source = launcher_root / "tools/tests/java/DevelopmentOracleProbe.java"
    source.parent.mkdir(parents=True)
    shutil.copyfile(HERE / "java/DevelopmentOracleProbe.java", source)
    (launcher_root / "pom.xml").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>'
        '<ojdbc.version>TEST_FIXTURE</ojdbc.version></properties></project>', encoding="utf-8")
    with zipfile.ZipFile(library / "ojdbc11-TEST_FIXTURE.jar", "w") as jar:
        for compiled in fake_classes.rglob("*.class"):
            jar.write(compiled, compiled.relative_to(fake_classes).as_posix())
    config = dict(runner.TEMPLATE, jdbc_url=BASE_FIELDS[0],
                  expected_db_unique_name=BASE_FIELDS[3], expected_service_name=BASE_FIELDS[4],
                  expected_con_name=BASE_FIELDS[5], cert_table=BASE_FIELDS[6], info_table=BASE_FIELDS[7])
    runner.validate_config(config)
    cases = {"success": None, "identity_mismatch": "DB_IDENTITY_MISMATCH",
             "connection_failure": "CONNECTION_FAILED"}
    for mode, expected in cases.items():
        audit = temporary / ("launcher-" + mode + ".audit")
        child_env = {"PATH": env.get("PATH", "/usr/bin:/bin"), "LANG": "C", "LC_ALL": "C",
                     "PROBE_STUB_MODE": mode, "PROBE_STUB_AUDIT": str(audit)}
        captured_out, captured_err = io.StringIO(), io.StringIO()
        before = set((launcher_root / "target/test-runs").glob("dev-oracle-*"))
        # JAVA_HOME is only the JDK already verified above; every endpoint and
        # credential passed to preflight is synthetic and handled by the stub.
        with patch.object(runner, "ROOT", launcher_root), \
                patch.object(runner, "child_environment", return_value=child_env), \
                patch.object(runner, "secret_input", side_effect=BASE_FIELDS[1:3]), \
                patch.dict(os.environ, {"JAVA_HOME": str(Path(java).resolve().parent.parent)}), \
                contextlib.redirect_stdout(captured_out), contextlib.redirect_stderr(captured_err):
            code = runner.preflight(config)
        require(code == (0 if expected is None else 1), "LAUNCHER_EXIT_FAILED")
        require(captured_err.getvalue() == "", "LAUNCHER_STDERR_NOT_EMPTY")
        reports = set((launcher_root / "target/test-runs").glob("dev-oracle-*")) - before
        require(len(reports) == 1, "LAUNCHER_REPORT_COUNT_FAILED")
        report = reports.pop()
        summary = json.loads((report / "summary.json").read_text(encoding="utf-8"))
        require(summary.get("status") == ("PASS" if expected is None else "FAIL"), "LAUNCHER_STATUS_FAILED")
        if expected is not None:
            require(summary.get("failureCode") == expected, "LAUNCHER_FAILURE_CODE_FAILED")
        require(summary.get("scope") == "READ_ONLY_DEVELOPMENT_PREFLIGHT", "LAUNCHER_SCOPE_FAILED")
        rendered = captured_out.getvalue()
        for name in ("summary.json", "index.html"):
            artifact = report / name
            require(stat.S_IMODE(artifact.stat().st_mode) == 0o600, "LAUNCHER_REPORT_PERMISSIONS_FAILED")
            rendered += artifact.read_text(encoding="utf-8")
        for sentinel in ("SECRET", *BASE_FIELDS[:8]):
            require(sentinel not in rendered, "LAUNCHER_PRIVATE_OUTPUT_LEAK")
        require(not (launcher_root / "target/.mobilecert-test-lock").exists(), "LAUNCHER_LOCK_LEAK")
        require(audit.is_file(), "LAUNCHER_STUB_NOT_EXECUTED")
        events = audit.read_text(encoding="utf-8").splitlines()
        if mode != "connection_failure":
            require(events[-2:] == ["rollback", "connection_close"], "LAUNCHER_CLEANUP_FAILED")
    return len(cases)


def main():
    try:
        env = clean_environment()
        java, javac = find_jdk21(env)
        # TemporaryDirectory is private and outside the application's target/lib.
        with tempfile.TemporaryDirectory(prefix="mobilecert-probe-offline-") as directory:
            temporary = Path(directory)
            classes, fake_classes = temporary / "probe", temporary / "fake-driver"
            for source, output in (
                (HERE / "java/DevelopmentOracleProbe.java", classes),
                (HERE / "fixtures/oracle/jdbc/OracleDriver.java", fake_classes),
            ):
                output.mkdir()
                result = run([javac, "--release", "21", "-proc:none", "-Xlint:all", "-Werror",
                              "-encoding", "UTF-8", "-d", str(output), str(source)], env=env, timeout=30)
                require(result.returncode == 0 and result.stdout == b"" and result.stderr == b"",
                        "COMPILATION_FAILED")
            input_count = verify_input_cases(java, classes, env)
            sql_count = verify_sql_cases(java, classes, fake_classes, temporary, env)
            launcher_count = verify_launcher_cases(java, fake_classes, temporary, env)
        print(f"PASS development_probe: input={input_count} sql={sql_count} launcher={launcher_count}")
        return 0
    except VerificationFailure as failure:
        print(f"FAIL development_probe: {failure}")
        return 1
    except Exception:
        print("FAIL development_probe: OFFLINE_TEST_ENVIRONMENT_FAILURE")
        return 1


if __name__ == "__main__":
    sys.exit(main())
