# Isolated migration tests

For the development workstation and the **existing development Oracle SMS
schema**, see [the Korean execution guide](../../docs/development-testing.md).
`verify-dev-oracle.sh` provides a credential-prompted, read-only preflight; it
does not create/drop objects or run authentication requests. Do not redirect the
Docker-specific Oracle runner below at a shared development database.

Use JDK 8 with `mvn -Pjava8 clean verify`, or JDK 21 with
`mvn -Pjava21 clean verify`; see [the dual-JDK build guide](../../docs/dual-jdk-build.md).
Both profiles run the same regression tests. They use the selected real HikariCP with an in-memory JDBC
driver, and tests production protocol classes against synthetic Java 8 golden
bytes with the JVM default deliberately set to UTF-8. No deployment config or
network service is required.

Run `python3 tools/tests/compare-runtimes.py --help` for the Java 8/21 comparison.
It compiles the original pinned Git revision and the current sources separately.
The comparison includes 13 protocol cases and six cases through the actual KCB
and LG U+ communication classes with a non-network SocketManager. Temporary
snapshots contain only generated test fixtures and are deleted on exit.

The original JDBCPOOL jar is optional, must be authentic, and is not included.
With `--legacy-pool-jar`, the script also compiles all original sources on Java 8
and characterizes transaction behavior using that pool plus RecordingDriver.
Original-version Log4j 2.17.1 jars must be in the specified Maven repository.
It does not restore the missing production ojdbc14 driver or validate Oracle.

`LegacyJdbcBaseline` deliberately checks the old partial-success behavior:
the first write is durable and the DAO can return true when its second write
fails. This records compatibility, not a recommendation for transaction design.

See [the migration report](../../docs/jdk21-hikari-selective.md) for decisions,
artifact provenance, behavior differences, and operational checks still needed.

## Repeatable verification and reports

From the project root with JDK 8 or 21, Maven and Python 3:

```sh
tools/tests/verify-mobilecert.sh regression
tools/tests/verify-mobilecert.sh readiness
tools/tests/verify-mobilecert.sh coverage
```

Each command is a separate gate and exits nonzero for failures. Open
`target/test-runs/<mode>/index.html` in a browser for the test list; the same
directory contains `summary.json`, Surefire XML, and the Maven log. `coverage`
also generates `target/site/jacoco/index.html` and `jacoco.xml` for SonarQube.
The coverage report is generated even if assertions fail; this does not change
the failing exit status. Do not run modes concurrently or run Maven concurrently
with the script in the same checkout.

The script selects the matching runtime profile. Run `clean verify` after
switching JDKs to remove the other profile's dependency jars. The Oracle
preflight and Docker Oracle wrappers remain JDK 21-only.

The user confirmed LG U+ is not used in the current service. The default
regression/readiness/coverage modes exclude its direct transport tests tagged
`inactive-lgu`. Focus release assessment on KCB and shared TCP/DB/Hikari paths.
Historical protocol/DAO compatibility fixtures still include legacy data layouts.
Run optional LG U+ diagnostics separately with
`tools/tests/verify-mobilecert.sh legacy-lgu` (or `mvn -Plegacy-lgu test`);
its known failures are not active KCB release failures.

`readiness` runs desired failure-handling assertions tagged with `readiness`.
These deliberately fail while the production defects remain. They are excluded
from the compatibility suite (`mvn test`), but **must be included in release
validation** (`mvn -Preadiness test`). A passing regression suite is not a release
approval. The regression suite verifies protocol bytes and explicitly EUC-KR
standard streams with a UTF-8 JVM default. The launcher no longer requires
the whole JVM to use EUC-KR, and the run script does not set file.encoding.

All test data is synthetic. Provider tests bind only to ephemeral loopback ports,
and DAO tests use RecordingDriver or JDBC interface stubs. Startup tests run a child JVM from an isolated
temporary directory, load synthetic UTF-8/EUC-KR XML and start/close a port-0
listener without sending requests. They also verify Korean logging using explicit
test appender encodings. No deployment XML or real DB/provider endpoint is read.
First-time Maven artifact downloads still require a Maven repository.
Normal tests include real Hikari contention and production provider communication
classes against a local mock peer, but do not validate Oracle SQL or full Gateway
STEP1/STEP2 orchestration. RecordingDriver does not check SQL binding or constraints.

Java 8 comparison remains optional tooling and requires an actual Java 8 JDK:

The comparison runs the original on Java 8/EUC-KR and the candidate on
Java 8 with EUC-KR/UTF-8 defaults and Java 21 with a UTF-8 default. EUC-KR is still explicit in the candidate's protocol code and
compiler source encoding; it is not required as the JVM default.

```sh
tools/tests/verify-mobilecert.sh compare --java8-home /path/to/jdk8 --java21-home /path/to/jdk21
```

See [the current verification report](../../docs/auth-test-readiness.md) for
source findings, known failing cases, and remaining integration gates.

## Application logging regression

The normal regression/coverage modes also run `LogSafetyTest`,
`ProviderLoggingTest`, `ServerLoggingTest` and `DaoLoggingTest` (20 cases).
They capture the real application logger to check levels, request ID separation
and cleanup, bounded exception metadata, and absence of synthetic private values.
The KCB tests assert unchanged full request/response bytes as well as logs.
They do not alter existing failure handling or validate Hikari's independent JUL
output, deployment appenders, real Oracle/KCB, or production load.

```sh
# Focused logging checks, using only synthetic inputs and loopback/stub I/O.
mvn -Dtest=LogSafetyTest,ProviderLoggingTest,ServerLoggingTest,DaoLoggingTest test
# Full compatibility suite and its local HTML result, including logging checks.
tools/tests/verify-mobilecert.sh regression
```

See [the logging change record](../../docs/logging-improvement.md) for event
meanings, log configuration checks and remaining limitations.

## Real Oracle integration

The user authorized synthetic tables in the existing local Docker Oracle.
Run `tools/tests/verify-oracle.sh` to create a dedicated disposable PDB, execute
real-driver KCB DAO/Hikari tests, then remove only the generated PDB. Default
JUnit runs exclude the `oracle` tag; Oracle tests fail if explicitly selected
without the guarded local environment, rather than silently skipping.

`--keep` retains the test PDB and private state in ignored `target/` for reuse.
`--cleanup` verifies ownership before dropping it. The normal Oracle profile
and the desired failure-handling profile are reported separately. On the
current source the combined command returns failure because two existing
business/security defects are reproduced, while all 12 normal Oracle cases pass.

See [Oracle setup, results and limits](../../docs/oracle-integration.md).
