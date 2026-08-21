# JDK 21 migration gates

1. **Inventory:** source tree, entry points, dependencies, configuration, deployment and sensitive data are documented.
2. **Java 8 baseline:** clean compile/package and available tests are reproducible with recorded tool versions.
3. **Protocol baseline:** sanitized golden byte tests cover every changed MCI, KCB and LG U+ path.
4. **Dependency recovery:** missing system-scope artifacts are restored or replaced from an approved source.
5. **JDK 21 compile:** compiler errors and removed APIs are fixed without functional redesign.
6. **Behavior comparison:** Java 8 and 21 produce identical protocol bytes and equivalent DB/provider requests.
7. **Operational validation:** startup, shutdown, timeout, logging redaction and deployment scripts are verified in an isolated environment.
8. **Cutover:** rollback instructions, monitoring signals and ownership are recorded before deployment.
