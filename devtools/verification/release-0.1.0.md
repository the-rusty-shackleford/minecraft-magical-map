# Release verification — 0.1.0

Rusty approved the interactive playtest and authorized release on 2026-09-20.

The Java 21 `./gradlew clean build --offline` completed successfully: six plain
JUnit tests (none skipped), twelve real-server GameTests, and the complete muted
client walkthrough. The client used hardware OpenGL and Complementary Unbound
5.8.1 with the actual optional integration jars recorded in
[the detailed verification record](0.1.0.md). The rendered atlas was inspected.

Production artifact: `magicalmap-0.1.0.jar`.

- SHA-1: `a10a1b7d8fe958dfdd287798785117f4d0727358`
- SHA-256: `8e485fcfafe129cf040f5b258253b7dc99c959c632fc541ed01963cc0a036190`

The artifact is byte-identical to the reviewed candidate. It includes the public
provider API and excludes test fixtures and optional upstream jars. The later
retained-instance launcher defaults affect only development tooling, not these
production bytes. The existing practice save is preserved and resuming it is the
default; rebuilding that course requires explicit `--reset`.

The release requires matching client and server installations. This gate does
not establish WAN latency behavior or full-pack survival balance; those limits
remain as recorded in the detailed verification evidence.
