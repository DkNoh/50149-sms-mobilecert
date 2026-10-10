#!/usr/bin/env python3
"""Offline checks for the development launcher. Never connects to Oracle."""
import importlib.util
import contextlib
import io
import json
import os
from pathlib import Path
import stat
import struct
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('development_oracle',
        Path(__file__).with_name('development-oracle.py'))
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


def config():
    return dict(runner.TEMPLATE, jdbc_url='jdbc:oracle:thin:@//example.invalid:1521/DEVTEST',
                expected_db_unique_name='SYNTHETIC', expected_service_name='DEVTEST',
                expected_con_name='SYNTHETIC_PDB', cert_table='SYNTH_CERT202610',
                info_table='SYNTH_INFO202610')


class DevelopmentLauncherTest(unittest.TestCase):
    def test_no_url_options_credentials_or_sql_identifiers(self):
        for key, value in (
                ('jdbc_url', 'jdbc:oracle:thin:account/secret@example.invalid:1521/DEVTEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid:1521/DEVTEST?option=1'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid:65536/DEVTEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//-example.invalid:1521/DEVTEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example..invalid:1521/DEVTEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid.:1521/DEVTEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid:1521/DEV$TEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid:1521/DEV#TEST'),
                ('jdbc_url', 'jdbc:oracle:thin:@//example.invalid:1521/_DEVTEST'),
                ('cert_table', 'T WHERE 1=1'), ('info_table', 'OTHER.T')):
            with self.subTest(key=key):
                candidate = config()
                candidate[key] = value
                with self.assertRaises(runner.SafeFailure):
                    runner.validate_config(candidate)

    def test_config_rejects_secret_fields_and_placeholders(self):
        for candidate in (dict(config(), password='SYNTHETIC_SECRET'),
                          dict(config(), environment='production'), runner.TEMPLATE,
                          dict(config(), expected_db_major_version=23)):
            with self.assertRaises(runner.SafeFailure):
                runner.validate_config(candidate)

    def test_valid_config_and_private_file(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'settings.json'
            path.write_text(json.dumps(config()), encoding='utf-8')
            path.chmod(0o600)
            self.assertEqual(config(), runner.read_config(str(path)))
            path.chmod(0o644)
            with self.assertRaises(runner.SafeFailure):
                runner.read_config(str(path))

    def test_private_parent_and_no_symlink(self):
        with tempfile.TemporaryDirectory() as temp:
            parent = Path(temp)
            path = parent / 'settings.json'
            path.write_text(json.dumps(config()), encoding='utf-8')
            path.chmod(0o600)
            alias = parent / 'alias.json'
            alias.symlink_to(path)
            with self.assertRaises(runner.SafeFailure):
                runner.read_config(str(alias))
            parent.chmod(0o755)
            with self.assertRaises(runner.SafeFailure):
                runner.read_config(str(path))
            parent.chmod(0o700)

    def test_duplicate_config_key_rejected(self):
        with self.assertRaises(runner.SafeFailure):
            json.loads('{"environment":"development","environment":"production"}',
                       object_pairs_hook=runner.unique_object)

    def test_repo_path_rejected_without_creation(self):
        with self.assertRaises(runner.SafeFailure):
            runner.external_path(str(runner.ROOT / 'target/private.json'))

    def test_binary_payload_preserves_special_characters_without_evaluation(self):
        synthetic = 'sYn$`"\\!\uac00'
        data = runner.payload(config(), 'SYNTHETIC_USER', synthetic)
        values = []
        offset = 0
        while offset < len(data):
            size, = struct.unpack('>I', data[offset:offset + 4])
            offset += 4
            values.append(data[offset:offset + size].decode('utf-8'))
            offset += size
        self.assertEqual(9, len(values))
        self.assertEqual(synthetic, values[2])

    def test_report_rejects_unexpected_output_and_inconsistent_exit(self):
        for raw, code in (
                ({'status': 'PASS', 'url': 'SYNTHETIC_PRIVATE'}, 0),
                ({'status': 'PASS'}, 1),
                ({'status': 'PASS'}, 0),
                ({'status': 'FAIL'}, 1),
                ({'status': 'FAIL', 'failureCode': 'line\nbreak'}, 1),
                ({'status': 'PASS', 'dbMajorVersion': True}, 0)):
            with self.assertRaises(runner.SafeFailure):
                runner.safe_result(json.dumps(raw), code)
        failure = {'status': 'FAIL', 'failureCode': 'IDENTITY_MISMATCH',
                   'sqlState': 'UNKNOWN', 'errorCode': 0}
        self.assertEqual(failure, runner.safe_result(json.dumps(failure), 1))
        passed = {'status': 'PASS', 'checks': runner.COMPLETED_CHECKS,
                  'dbMajorVersion': 19, 'driverMajorVersion': 23,
                  'charset': 'AL32UTF8', 'ncharset': 'AL16UTF16'}
        self.assertEqual(passed, runner.safe_result(json.dumps(passed), 0))
        for invalid in (dict(passed, dbMajorVersion=23),
                        dict(passed, checks=runner.COMPLETED_CHECKS[:-1]),
                        dict(passed, failureCode='CONNECTION_FAILED')):
            with self.assertRaises(runner.SafeFailure):
                runner.safe_result(json.dumps(invalid), 0)

    def test_java_options_and_secrets_not_in_child_environment(self):
        with patch.dict(os.environ, {'JAVA_TOOL_OPTIONS': '-Dsynthetic.secret=value',
                                    'TNS_ADMIN': '/synthetic', 'DB_PASSWORD': 'SYNTHETIC_SECRET'}):
            self.assertEqual({'PATH', 'LANG', 'LC_ALL'}, set(runner.child_environment()))

    def test_report_files_private_without_relying_on_shell_umask(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(runner, 'ROOT', Path(temp)):
            with contextlib.redirect_stdout(io.StringIO()):
                runner.write_report({'status': 'FAIL', 'failureCode': 'PROBE_TIMEOUT'})
            reports = list((Path(temp) / 'target/test-runs').glob('dev-oracle-*'))
            self.assertEqual(1, len(reports))
            for name in ('index.html', 'summary.json'):
                self.assertEqual(0o600, stat.S_IMODE((reports[0] / name).stat().st_mode))

    def test_getpass_echo_fallback_is_an_error(self):
        def unsafe_fallback(_):
            import warnings
            warnings.warn('synthetic no tty', runner.getpass.GetPassWarning)
            return 'SYNTHETIC_SECRET'
        with patch.object(runner.getpass, 'getpass', side_effect=unsafe_fallback):
            with self.assertRaises(runner.getpass.GetPassWarning):
                runner.secret_input('synthetic prompt')


if __name__ == '__main__':
    unittest.main()
