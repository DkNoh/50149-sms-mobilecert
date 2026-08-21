---
name: jdk21-migration
description: Plan, implement, and verify a staged migration of this legacy Java 8 mobile-cert Maven project to JDK 21 without changing authentication protocol behavior. Use for build recovery, dependency replacement, compiler and Maven updates, removed-JDK-API remediation, charset hardening, test gates, or JDK 8 versus 21 behavior comparisons. Do not perform a big-bang migration when the Java 8 baseline or protocol fixtures are unavailable.
---

# Migrate to JDK 21

1. Read \`references/gates.md\`.
2. Recover a reproducible Java 8 build before changing source or target levels.
3. Inventory system-scoped and missing JARs, deprecated APIs, reflection, charset defaults, XML parsing, logging, Oracle JDBC, and deployment assumptions.
4. Add or recover automated tests, prioritizing protocol bytes and startup configuration.
5. Upgrade dependencies and build plugins separately from language changes.
6. Compare Java 8 and JDK 21 outputs at every gate.
7. Keep compatibility changes in small commits and report every unavailable gate.

## Rules

- Do not raise \`maven.compiler.source\` or \`target\` merely to make the current machine compile.
- Replace obsolete or missing dependencies with traceable coordinates or documented internal artifacts; do not invent binaries.
- Make charset conversions explicit only with golden byte evidence.
- Keep DB and external provider tests isolated behind fakes or authorized test environments.
- Preserve a rollback point after each completed gate.
