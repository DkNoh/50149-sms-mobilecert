# Analysis baseline

- The project is a Maven JAR targeting Java 8 with EUC-KR source encoding.
- Runtime behavior depends on JARs expected under \`libs/\`; the current directory may not contain them.
- Runtime configuration is loaded from external service XML and provider configuration that may be absent.
- The service accepts internal MCI traffic, records data in Oracle, and calls KCB or LG U+ paths.
- Test-like classes under \`src/main/java/scbk/cert/test\` are manual utilities, not a reliable automated suite.
- Treat phone numbers, authentication values, CI/DI, full packets, DB credentials, and provider configuration as sensitive.
- Static analysis cannot establish production charset, exact packet samples, DB schema, firewall behavior, or external provider responses.
