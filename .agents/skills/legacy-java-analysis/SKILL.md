---
name: legacy-java-analysis
description: Analyze this repository's legacy Java mobile-cert architecture, control flow, dependencies, data boundaries, and operational risks using bounded source evidence. Use for codebase mapping, impact analysis, incident investigation, dependency recovery, or questions about how MCI, KCB, LG U+, Oracle, server, DAO, and configuration code interact. Do not use for implementing JDK 21 migrations or byte-protocol changes when their dedicated skills apply.
---

# Analyze legacy Java

1. Read \`references/baseline.md\`.
2. Refresh \`tools/code-index/out/index.json\` when it is missing or older than the source files.
3. Start from the named behavior or nearest entry point. Inspect directly connected files before widening scope.
4. Trace inputs, parsing, DB effects, external calls, responses, logging, and failure paths.
5. Separate source-proven facts, inferred behavior, and missing runtime evidence.
6. Do not connect to production-like DB or network endpoints.
7. Report findings by severity with file and symbol evidence, uncertainty, and the smallest useful validation.

## Output

- Scope examined
- Control and data flow
- Findings with evidence
- Unknowns and unavailable dependencies
- Recommended next validation
