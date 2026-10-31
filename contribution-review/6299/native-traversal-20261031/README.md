# Jackson #6299 native-traversal correction: validation evidence

The correction in [owned draft PR #8](https://github.com/agamal026/jackson-databind/pull/8) passed all four existing hosted builds and the Linux Java 17 static Android API check.
This is owned-fork validation, not maintainer acceptance or another external submission.
The source of [upstream PR #6302](https://github.com/FasterXML/jackson-databind/pull/6302) remains unchanged; its separate access and personal CLA gates remain unresolved.

## Exact source

| Identity | SHA |
| --- | --- |
| Published correction | `b24dbb93b0a10dbd04cbe211c9fb07cc76a3fae4` |
| Reviewed and tested tree | `3194dde3792105bfcb47afac0a8adc2863a8c204` |
| Parent marker candidate | `a100335fbabebf261322c37248201e974563dae4` |
| Preserved prepatch base | `5d64697ef018099e3d5f951668752ec9d4ffabf9` |
| Actual hosted merge checkout | `b765c3b5e11faf40a1ee0c5dcbf616fa66d2be3b` |

The GitHub Git-data API confirms the source and tested merge share the reviewed tree.
All four full logs name that merge checkout.
The evidence commit adds documentation only; validation belongs to the source commit above.

## Problem and change

The earlier singleton preflight can skip public contents overrides and evaluate an inclusion decision twice.
The later Iterable context marker also emits an extra empty array when an override writes its own value before delegating, and can resurrect output intentionally removed by a real filtering generator.

The correction makes the suppression decision during the native contents traversal across the 14 affected serializer paths.
A private per-call boolean is cleared before a retained element or null serializer is invoked.
A stateless shared helper writes an empty array only when native inclusion suppressed all contents of the unwrapped singleton.
This removes the preflight and context marker without another traversal or generator-output inference.
Existing hooks, value/generator identity, typed paths, null handling, exception references, and primitive bulk writes are preserved.

**Extension boundary:** the new fallback applies to exact built-in instances where contents dispatch is extensible.
Custom subclasses, including subclasses inheriting contents unchanged, retain their historical output behavior and responsibility for the output shape.
The correction does not claim automatic valid empty-array output for arbitrary custom serializers.

## Executed hosted matrix

The unchanged workflow command is `./mvnw -B -ff -ntp verify`.
These hosted invocation blocks do not contain an explicit timezone override.

| Job | Actual runtime | Reported | Passed | Failures/errors | Skipped | Contribution cases |
| --- | --- | ---: | ---: | --- | ---: | ---: |
| [Linux 17](https://github.com/agamal026/jackson-databind/actions/runs/41236619589/job/124641646653) | Ubuntu 24.04.5, Temurin 17.0.21+9 | 5,955 | 5,954 | 0/0 | 1 | 146/146 |
| [Windows 17](https://github.com/agamal026/jackson-databind/actions/runs/41236619589/job/124641646437) | Windows Server 2025, Temurin 17.0.20+101 | 5,955 | 5,954 | 0/0 | 1 | 146/146 |
| [Linux 21](https://github.com/agamal026/jackson-databind/actions/runs/41236619589/job/124641646492) | Ubuntu 24.04.5, Temurin 21.0.13+9 | 5,957 | 5,956 | 0/0 | 1 | 146/146 |
| [Linux 25](https://github.com/agamal026/jackson-databind/actions/runs/41236619589/job/124641646487) | Ubuntu 24.04.5, Temurin 25.0.5+8 | 5,957 | 5,956 | 0/0 | 1 | 146/146 |

Each build continues after tests through JAR creation, CycloneDX BOM generation, Gradle module metadata, JaCoCo reporting, and the JaCoCo execution-file requirement before `BUILD SUCCESS`.
Linux 17 then passes the prescribed `./mvnw -B -q -ff -ntp -DskipTests animal-sniffer:check` step.
This is static Android API compatibility, not device execution.
The two downstream-trigger jobs are skipped by the existing workflow conditions.

Linux 17's optional base-coverage artifact download reports no matching artifact and is tolerated by the unchanged workflow.
Current coverage reporting completes; no coverage comparison or improvement is claimed.

## Local controls and preserved failures

| Execution | Reported | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Corrected focused contribution suite | 146 | 146 | 0 | 0 | 0 |
| Exact prepatch compatibility controls | 42 | 42 | 0 | 0 | 0 |
| Previous marker production, same 56 new cases | 56 | 16 | 26 | 14 | 0 |
| Corrected full suite, Europe/London | 5,955 | 5,952 | 2 | 0 | 1 |
| Exact prepatch two date tests, Europe/London | 2 | 0 | 2 | 0 | 0 |
| Exact prepatch same two date tests, UTC | 2 | 2 | 0 | 0 | 0 |
| Corrected complete clean verify, explicit UTC | 5,955 | 5,954 | 0 | 0 | 1 |

The local runtime was Linux x86_64, OpenJDK 17.0.20 and Maven wrapper 3.9.11.
The first full run failed in `MapDeserializationTest.testDateMap` and `testCalendarMap`.
Their source is byte-identical to the prepatch baseline.
Both failures reproduce on that unchanged baseline in Europe/London; the same bounded baseline tests pass in UTC.
The later complete UTC verification and separate static Android check pass without source changes, test exclusions or altered gates.
The precise date-parsing cause was not diagnosed, and the entire baseline suite was not rerun.
The single full-suite skip is `JDKTypeSerializationTest.test5194`.

Commands used task-private settings/cache and `MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=4'`.
The full UTC build used `TZ=UTC ./mvnw -B -ff -ntp -s "$TASK_SETTINGS" -Dmaven.repo.local="$TASK_CACHE" clean verify` with those JVM options; it completed at 19:49:27 UTC on 31 October 2026.
The subsequent quiet Android command returned exit0; its empty log is retained and its process outcome is recorded in the receipt.
Runtime settings are intentionally excluded from this public packet.

The archive also retains an initial dependency-resolution failure before compilation and two incomplete attempts whose logs end without a trustworthy test outcome.
Those incomplete attempts are not counted as tests or assigned an invented failure cause.
Current snapshot dependency bytes were not pinned to historical October 18 runs.

## Test and review accounting

The 146 contribution invocations comprise 53 original regression, 21 compatibility, 12 traversal, 2 override, 2 snapshot, 4 unchanged prefix, 30 single-pass/filter and 22 subclass-hook cases.
The new subclass matrix uses directly configured serializers; it does not prove contextualization for every subtype.
The existing contextual Iterable override cases are retained.
The inclusion/snapshot probes are constructed compatibility cases, not reported downstream incidents.

One marker-era direct-contents expectation changed from `[]` to empty output after exact-baseline execution proved that contents-only calls do not own an array wrapper.
Five marker context-attribute lifecycle tests were removed with the mechanism.
Behavioral tests retain direct, wrapped, typed, override, re-entry, prefix and generator-filter coverage.

Two independent AI reviewers inspected the frozen source and associated behavior tests; they subsequently inspected all four hosted logs.
No blocking issue was found within the stated built-in scope.
No human review or acceptance is claimed.
There was one production correction cycle, with source hashes checked before publication.

## Archived evidence

- [Complete evidence bundle](evidence.tar.gz), SHA256 `7c198a9aed1a2668c3278888c854a2f0a43bf687191bd1f2d7275e416aaefb21`.
- [Per-file hashes](manifest.json).
- [Structured receipt](receipt.json).

The 20-file bundle contains the complete decoded four hosted logs, 13 local attempt logs, the reviewed source manifest, structured receipt, and per-file manifest.
Log transformations are limited to UTF-8 BOM removal, newline normalization, and redaction of local workspace/proxy identifiers.
Raw Surefire environment-property records and runtime settings are excluded.
The archive preserves failed and incomplete attempts alongside successful execution.
