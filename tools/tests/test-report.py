#!/usr/bin/env python3
"""Summarize isolated Surefire results without copying raw packet/log output."""
import html
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

mode, build_status = sys.argv[1], int(sys.argv[2])
root = Path(__file__).resolve().parents[2]
destination = root / 'target/test-runs' / mode
destination.mkdir(parents=True, exist_ok=True)
rows = []
for path in sorted((root / 'target/surefire-reports').glob('TEST-*.xml')):
    shutil.copy2(path, destination / path.name)
    suite = ET.parse(path).getroot()
    for case in suite.findall('testcase'):
        failure = case.find('failure')
        error = case.find('error')
        issue = failure if failure is not None else error
        state = 'FAIL' if issue is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
        rows.append({'class': case.get('classname', ''), 'test': case.get('name', ''),
                     'status': state, 'seconds': case.get('time', ''),
                     'message': '' if issue is None else issue.get('message', '')[:1200]})
counts = {state: sum(row['status'] == state for row in rows) for state in ('PASS', 'FAIL', 'SKIP')}
passed = build_status == 0 and bool(rows) and counts['FAIL'] == 0 and counts['SKIP'] == 0
scope = ('Real Oracle JDBC / Hikari / KCB DAO; dedicated synthetic local PDB, inferred DDL.'
         if mode in ('oracle', 'oracle-readiness') else
         'Unused LG U+ diagnostics only; excluded from the active-provider release gate.'
         if mode == 'legacy-lgu' else 'KCB / common regression; inactive LG U+ transport checks excluded.')
summary = {'mode': mode, 'time_utc': datetime.now(timezone.utc).isoformat(),
           'result': 'PASS' if passed else 'FAIL', 'build_status': build_status,
           'counts': counts, 'cases': rows,
           'scope': scope + (' No production Oracle or real KCB endpoint used.' if mode.startswith('oracle')
                            else ' Synthetic JDBC / loopback only; no Oracle or real provider validation.')}
(destination / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
escape = html.escape
table = ''.join('<tr><td>' + '</td><td>'.join(escape(str(row[key])) for key in
                ('status', 'class', 'test', 'seconds', 'message')) + '</td></tr>' for row in rows)
coverage = ''
if mode == 'coverage':
    coverage = '<p><a href="../../site/jacoco/index.html">JaCoCo coverage HTML</a> · <a href="../../site/jacoco/jacoco.xml">SonarQube import XML</a></p>'
note = ('실제 로컬 Oracle과 JDBC 드라이버를 사용한 합성 스키마 검증입니다. 테이블은 코드에서 추정한 테스트용 DDL이며 운영 DB 구조·제약과 동일하다는 의미가 아닙니다. 실제 KCB 인증은 호출하지 않습니다.'
        if mode.startswith('oracle') else
        '합성 데이터와 가짜 JDBC·로컬 모의 통신으로 검증한 결과입니다. 실제 본인인증 성공, Oracle 호환성, 운영 부하를 보장하지 않습니다.')
page = f'''<!doctype html><html lang="ko"><meta charset="utf-8">
<title>mobilecert {escape(mode)} test report</title>
<style>body{{font:15px system-ui;margin:32px;color:#172534;background:#f5f7fa}}h1{{font-size:26px}}table{{border-collapse:collapse;background:white;width:100%}}td,th{{border:1px solid #ced7df;padding:10px;text-align:left;vertical-align:top;overflow-wrap:anywhere}}.note{{padding:16px;background:#fff1cf}}pre{{white-space:pre-wrap}}</style>
<h1>mobilecert · {escape(mode)} · {summary['result']}</h1>
<p>{escape(summary['time_utc'])} · PASS {counts['PASS']} / FAIL {counts['FAIL']} / SKIP {counts['SKIP']}</p>
<p>{escape(scope)}</p>
<p class="note">{escape(note)} regression 통과만으로 배포 가능 판정을 내리지 마세요. readiness는 알려진 장애 처리 결함을 재현하며 현재 코드에서 실패할 수 있습니다.</p>
<p>Build status: {build_status}. {'No tests were discovered; this is a failure.' if not rows else ''}</p>
{coverage}<p><a href="summary.json">JSON</a> · <a href="environment.txt">JDK/Maven environment</a></p>
<table><thead><tr><th>결과</th><th>클래스</th><th>검증 항목</th><th>초</th><th>실패 이유</th></tr></thead><tbody>{table}</tbody></table>
</html>'''
(destination / 'index.html').write_text(page, encoding='utf-8')
print(f"{mode}: {summary['result']} {counts}")
sys.exit(0 if passed else 1)
