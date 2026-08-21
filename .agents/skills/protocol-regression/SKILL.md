---
name: protocol-regression
description: Verify and protect byte-level compatibility of this repository's fixed-width MCI, KCB, and LG U+ authentication messages. Use when changing parsers, serializers, field widths, padding, truncation, socket reads, encodings, message constants, or code that uses String and byte conversions. Use for building sanitized golden fixtures and protocol regression tests; do not use real customer packets or production endpoints.
---

# Verify protocol compatibility

1. Read \`references/checklist.md\`.
2. Identify the exact inbound and outbound message type, field order, widths, and expected charset.
3. Capture the current behavior with synthetic, non-sensitive golden bytes before changing code.
4. Assert byte length, field offsets, padding, truncation, Korean text, empty fields, maximum widths, short input, and malformed input.
5. Make the smallest implementation change.
6. Re-run golden tests for every affected direction and provider.
7. Stop if the production charset or authoritative packet specification is unknown; report the decision needed instead of guessing.

## Invariants

- Compare bytes, not Java character counts.
- Specify the charset at every conversion boundary once the authoritative value is established.
- Preserve field order and total message length unless a versioned protocol change is explicitly requested.
- Never log or commit raw customer messages, phone numbers, authentication values, CI, DI, keys, or credentials.
