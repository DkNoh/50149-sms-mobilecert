# Fixed-width protocol checklist

## Evidence

- Locate message constants, parser offsets, formatter utilities, and socket read/write loops.
- Establish whether lengths are bytes or characters from code and an authoritative specification.
- Record the charset and line or framing rules. Treat the platform default charset as unknown.

## Golden cases

- ASCII-only nominal message
- Korean text crossing a byte-width boundary
- Empty, one-byte-short, exact-width, and over-width fields
- Leading and trailing space preservation
- Partial socket reads and multiple messages in one read
- Invalid numeric fields, unknown transaction code, and malformed total length

## Safety

- Generate synthetic identifiers and numbers.
- Keep provider and DB endpoints disabled.
- Assert that diagnostics redact sensitive fields.
