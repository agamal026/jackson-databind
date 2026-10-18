# Jackson #6299 — evidence for the 3.1 follow-up

**Final owned-fork validation passed on all four build jobs.** Source `38261628a27c109e6e49c916cdb0f7e8bf7bbf45` passed all 53 original regression tests and all 21 compatibility tests per job. Whole-suite results are 5,883 tests on both JDK 17 jobs and 5,885 on JDK 21/25, with zero failures, zero errors, and one pre-existing skip. The test-only baseline and both failed intermediate attempts remain preserved. This is validation on the contributor-owned fork, not upstream acceptance or a report of upstream PR checks. Finalized at 2026-10-18T14:56:24.100841+00:00. [VALIDATION.json](VALIDATION.json) records each attempt and its exact source identity.

This packet supports the response to the branch and shared-helper feedback on [upstream PR #6302](https://github.com/FasterXML/jackson-databind/pull/6302), for [issue #6299](https://github.com/FasterXML/jackson-databind/issues/6299). Hosted runs are on [the contributor-owned validation PR #2](https://github.com/agamal026/jackson-databind/pull/2). Their synthetic PR merge commits identify CI checkouts; they do not establish an upstream merge or maintainer acceptance. The [original 3.x source](https://github.com/agamal026/jackson-databind/tree/archive/6299-original-3.x) at `de533c218b30a86ac9e74a6627b7224c2a0d2987` is retained separately. The [original 3.x evidence](https://github.com/agamal026/jackson-databind/blob/942fb1328e72dd76cf58a32b194d9d3783baad16/contribution-review/6299/README.md) is retained as earlier evidence, not substituted for this branch's results.

## Problem, branch, and change

When container-content filtering suppresses the only physical element and single-element unwrapping is enabled, existing serialization can write no JSON value. At the root that produces an empty string; inside a bean it can leave a property without a value and cause a later write to fail. The proposed behavior is an empty array for that suppressed singleton, while retained physical singletons continue to unwrap and physically larger containers keep their existing array path. The baseline below demonstrates the reported failure.

The [branch audit](JACKSON-6299-BRANCH-REVIEW.md) establishes 3.1 as the oldest affected maintained line from the inspected feature source and history: the feature is absent from 2.x and 3.0. This follows the [maintainer's branch request](https://github.com/FasterXML/jackson-databind/pull/6302#issuecomment-6250227789) and [repository branch strategy at the exact base](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/CLAUDE.md#branch-strategy).

The change adds one package-private, stateless helper used by fourteen existing physical-singleton unwrapping branches. It owns the filtering guard, lazy suppression query, and empty-array fallback. The individual serializers keep their original contents dispatch and public/protected signatures. The source review checked null handling, cached/generic/typed element lookup, enum declaring-class lookup, current-value assignment, and existing exception boundaries. The helper addresses the [maintainer's duplication request](https://github.com/FasterXML/jackson-databind/pull/6302#discussion_r4307148911).

The patch does not backport later content-aware container `isEmpty` behavior or change property-omission scope. A retained value can be queried by both the new probe and existing filtered contents path; exactly-once predicate evaluation is not guaranteed. The [helper design review](JACKSON-6299-HELPER-DESIGN-REVIEW.md) documents the actual existing Jackson precedent and the decision to retain original serializer dispatch. The [generator-count review](JACKSON-6299-ENTRY-COUNT-REVIEW.md) records why an apparently smaller generator-entry-count fallback was rejected using an official FilteringGeneratorDelegate counterexample.

## Exact source identities

| Role | Commit | Tree |
| --- | --- | --- |
| Upstream 3.1 base | `5d64697ef018099e3d5f951668752ec9d4ffabf9` | `fb99fe7d78a338331eacc58944fa9b89de0c41c0` |
| Test-only baseline | `4a47e457e7bf8e877bb837693eab58fdc256c22c` | `401a300365df0c4d5e501da374c55d14fc984875` |
| First implementation, test compilation failed | `c2366f76b7a3e1a2c707acb4e25df2e1cd3a6a00` | `93ddd45b71b193a9305ebe1d87e690998192e054` |
| API-corrected source, two compatibility failures per job | `60218d2e760de528fdc48748c8a844a34f3755c8` | `2eeb7de459109da30ccea323373d1f6c47ceba71` |
| Final validated source after fixture correction | `38261628a27c109e6e49c916cdb0f7e8bf7bbf45` | `861d2292ae59d0c91fd3b572cd044212e2580c00` |

[Source identities](SOURCE-IDENTITIES.json) contain all twelve exact Git blobs, source SHA-256 values, byte lengths, and commit parents. [source.patch](source.patch) is the complete binary-capable, full-index Git diff from the upstream base to the final validated source. The first attempt source.patch (`final-hosted/attempt-c2366f-failure/source.patch` in the archive), the API-corrected attempt patch (`final-hosted/attempt-60218d/source.patch` in the archive), the narrow [API correction diff](api-correction.patch), and the [fixture correction diff](fixture-correction.patch) are retained separately.

## Proven test-only baseline

[Run 39135075480](https://github.com/agamal026/jackson-databind/actions/runs/39135075480) completed with the expected regression failures on four jobs. Its actual checkout was synthetic merge `70e4328406ddbfc2c09e516c80a0e39b725c2789`, whose tree matches the test-only commit. The baseline adds one regression test file and changes no production source. The complete audit (`baseline-hosted/audit.json` in the archive) reconciles every class summary with the aggregate results and ties the checkout to the exact test blob.

Counts below are per job. F / E / S means failures, errors, and skipped; the final regression number is passed.

| Hosted job | Observed Java | Whole-suite tests | Whole-suite F / E / S | Added regression: total, F / E / S / passed |
| --- | --- | ---: | --- | --- |
| [build (17, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39135075480/job/117697828976) | 17.0.20+1 | 5862 | 1 / 32 / 1 | 53: 1 / 32 / 0 / 20 |
| [build (17, windows-latest, W)](https://github.com/agamal026/jackson-databind/actions/runs/39135075480/job/117697829071) | 17.0.20+101 | 5862 | 1 / 32 / 1 | 53: 1 / 32 / 0 / 20 |
| [build (25, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39135075480/job/117697829145) | 25.0.4+1 | 5864 | 1 / 32 / 1 | 53: 1 / 32 / 0 / 20 |
| [build (21, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39135075480/job/117697829198) | 21.0.12+1 | 5864 | 1 / 32 / 1 | 53: 1 / 32 / 0 / 20 |

All failures and errors belong to the added `JsonIncludeUnwrappedContainer6299Test` class. Its root assertion expected `[]` and received an empty string. Its 32 errors are stream-write errors from attempting a property name while the previous property still needs a value. Twenty controls passed in each job. The two extra whole-suite tests on JDK 21 and 25 belong to Java21CollectionsTest and passed. The pre-existing single skip is in JDKTypeSerializationTest. Android compatibility and downstream-trigger steps were skipped after the failed Build; no baseline Android success is claimed. Exact job identities and all full decoded logs are in baseline-hosted (`baseline-hosted/` in the archive).

The earlier local attempt (`local-baseline/attempt.json` in the archive) stopped while resolving the snapshot parent POM because DNS lookup for central.sonatype.com failed. It never reached compilation or testing and is not counted as a reproduction. Its receipt and short log are explicitly labeled portable derivatives with path substitutions and original/derived hashes.

## First implementation failure and correction

[Run 39136144887](https://github.com/agamal026/jackson-databind/actions/runs/39136144887) checked out synthetic merge `0e511dfb7df1044e7b0cc8b21612a9571eeb8dd5`, whose tree equals the first implementation's tree. **All four Build jobs failed test compilation because `JacksonException.Reference` has no `getFrom()` method on the 3.1 dependency. No tests ran.** Android steps and downstream triggers were skipped. The complete failed-attempt audit (`final-hosted/attempt-c2366f-failure/audit.json` in the archive), metadata, and all four full decoded logs are preserved.

The original source review did not catch this unavailable API. It explicitly did not claim compilation or test execution; that limitation matters here. Its exact historical wording and hashes are preserved. A [separate API correction review](JACKSON-6299-API-CORRECTION-REVIEW-PUBLIC.md) binds the one-token change to the official `Reference.from()` source and corrected commit. All ten production files and the compatibility test remain byte-identical to the original reviewed snapshot. The original test retains its reference-source identity assertion with the supported accessor.

## API-corrected attempt: two compatibility failures

[Run 39136668969](https://github.com/agamal026/jackson-databind/actions/runs/39136668969) checked out synthetic merge `a0ad81850bb6c85eab28c3fe637f90c796354b99`, whose tree matches source `60218d2e760de528fdc48748c8a844a34f3755c8`. The completed audit (`final-hosted/attempt-60218d/audit.json` in the archive), full decoded logs, metadata, and attempt source patch are preserved. All four builds compiled and executed both new test classes. The original 53 tests passed; the 21 compatibility tests had 19 passes and two failures on every job. The total of 74 is now an observed execution count for this attempt, with 72 passes and two failures, not an all-passing result.

The failures are both in `retainedCustomObjectAndContentType`: the ObjectArray case expected an empty array but retained the typed element, and the Iterable case with `fixed=false, typed=true` expected a type identifier that the output omitted. These observations alone do not establish whether the expectations describe unchanged 3.1 behavior or whether the production patch introduced the differences. The failure audit records exact expected/actual values and this causality limit.

Whole-suite counts are 5,883 tests on JDK 17 and 5,885 on JDK 21/25, with two failures, zero errors, and one pre-existing skip. No other test class failed. All class totals reconcile with the aggregates. Android compatibility and downstream-trigger steps were skipped after the Build failures, so no Android success is claimed.

## Final validation and fixture correction

Candidate `38261628a27c109e6e49c916cdb0f7e8bf7bbf45` changes only the compatibility fixture relative to the API-corrected attempt. All ten production files and the original 53-case regression class are unchanged. [The separate fixture review](jackson-6299-3.1-compatibility-fixture-review.md) identifies the exact new blob and concludes that the revised control is suitable for actual execution; it does not claim a pass.

The compatibility fixture now captures unwrapped output before obtaining a reference from the same writer with single-element unwrapping disabled. This avoids first warming the changed path's serializer cache. The reference requires an array with zero or one element, checks the retained nested value explicitly, and requires untyped empty content to be suppressed. It changes only the expected outer shape and compares complete direct and delegate output. The original explicit regression cases remain intact. This is a constrained check against the unchanged wrapped serialization path; it does not establish an unchanged-base reproduction of either earlier failed compatibility assumption.

[Final run 39137464915](https://github.com/agamal026/jackson-databind/actions/runs/39137464915) completed successfully. Each actual job checked out synthetic merge `086c5a281dbe8099cd3367d089ca97ce66bea449`; its tree `861d2292ae59d0c91fd3b572cd044212e2580c00` equals the final source tree. The completed audit is `final-hosted/attempt-382616/audit.json` inside the archive, with all four full decoded logs and canonical metadata. Its SHA-256 is `e77f12045388fa4ea2fd1ad6de2b8ae96a7e9d325147a0df3563d8aaaa8d6575`.

Counts below are per job; F / E / S means failures, errors, and skipped. The 74 contribution cases consist of the original 53 plus 21 compatibility cases, all actually executed and passing for this exact commit.

| Hosted job | Observed Java | Whole-suite tests | Whole-suite F / E / S | Contribution cases passed / run | Android compatibility step |
| --- | --- | ---: | --- | --- | --- |
| [build (17, windows-latest, W)](https://github.com/agamal026/jackson-databind/actions/runs/39137464915/job/117704796544) | 17.0.20+101 | 5883 | 0 / 0 / 1 | 74 / 74 | skipped |
| [build (21, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39137464915/job/117704796668) | 21.0.12+1 | 5885 | 0 / 0 / 1 | 74 / 74 | skipped |
| [build (17, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39137464915/job/117704796669) | 17.0.20+1 | 5883 | 0 / 0 / 1 | 74 / 74 | success |
| [build (25, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39137464915/job/117704796724) | 25.0.4+1 | 5885 | 0 / 0 / 1 | 74 / 74 | skipped |

The one skipped test remains in JDKTypeSerializationTest. The two additional tests on JDK 21/25 are passing Java21CollectionsTest cases; the audit reconciles every class total with the aggregate. No test class failed. Linux JDK 17 also passed the observed Animal Sniffer Android SDK compatibility check, `./mvnw -B -q -ff -ntp -DskipTests animal-sniffer:check`; the other three Android steps were skipped. This establishes that compatibility check, not Android device or emulator execution. Downstream-trigger jobs were skipped.

The run completed by its recorded update at 14:49:22 UTC. Final metadata was observed at 14:50:34–14:50:35 UTC and all full logs were retrieved by 14:51:03 UTC on 2026-10-18. The original source and correction reviews retain their earlier inspection states; the completed hosted evidence establishes execution separately. These are owned-fork draft PR #2 results, not upstream PR #6302 check results.

The test-source coverage includes retained and suppressed singleton behavior, physical multi-element controls, property wrap/unwrap overrides, null content, disabled filtering, root/bean output, custom content serialization, exception source/index/cause behavior, nested and typed reference contents, ordinary and filtering generator delegates, existing property-omission behavior, and enum serializer access to current value. The [main review](JACKSON-6299-3.1-SOURCE-REVIEW.md) distinguishes source enumeration from runtime proof and documents remaining scope and performance limits.

## Review and contribution limits

The four original reviews, the API correction review, and the separate fixture review are automated source analyses. No human review, personal learning attestation, or source-reviewer test execution is claimed. Hosted test execution is separate evidence and is reported only from completed job logs.

The observed command is `./mvnw -B -ff -ntp verify`. The Maven runtime version was not printed in these observed logs and is not inferred from a wrapper or executable path. Snapshot dependency version strings do not establish dependency artifact hashes or a fully pinned dependency environment. No runtime performance measurement is included.

The [maintainer's CLA reminder](https://github.com/FasterXML/jackson-databind/pull/6302#issuecomment-6250307295) and [Jackson contribution paperwork](https://github.com/FasterXML/jackson/blob/main/CONTRIBUTING.md#paperwork) remain relevant. The contributor's CLA status is **unverified**; no agent signed or attested to it. This packet does not establish upstream acceptance, reward eligibility, subscriptions, or earnings.

## Published files and full archive

The direct publication contains only the top-level files identified by `PUBLICATION.json`: this README, machine-readable validation and source identities, review documents, patches, the payload manifest, and the archive with its checksum. Full hosted audits, decoded logs, and raw API metadata are archive-only. Their repository-relative archive paths are shown as code throughout this page. The original API correction receipt is also preserved inside the archive; its clearly labeled publication copy adjusts the archive-only link display without changing the review's findings.

## Provenance and integrity

Hosted job logs are connector-returned decoded Unicode text saved as UTF-8. This packet copies those saved files byte-for-byte, preserving existing BOMs, LF/CRLF, and replacement characters. The Windows baseline log already contained 32 U+FFFD characters in the decoded response; those were preserved and do not occur in the relevant failure summaries. **Hashes describe saved UTF-8 representations, not original HTTP transport bytes.** Metadata JSON was originally parsed and saved as formatted JSON; this packet copies its saved bytes without reserialization. The local DNS receipt/log are labeled derivatives because their absolute local paths were replaced for portability.

`SHA256SUMS` covers the complete extracted payload, including nested hosted evidence and the original API correction receipt. It excludes itself, the archive, the separate archive checksum, and the external `PUBLICATION.json` allowlist. The archive includes that payload manifest and every payload file; it excludes the archive, its checksum, and the external allowlist to avoid recursive hashes. Archive metadata uses relative paths, normalized ownership, and zero timestamps; payload bytes are unchanged.

Verify the archive first, extract it into a new directory, then verify its payload. Running the payload manifest against only the directly published files will fail because the full logs and metadata must first be extracted.

```sh
sha256sum -c jackson-6299-3.1-followup-evidence.tar.gz.sha256
mkdir verified-3.1-evidence
tar -xzf jackson-6299-3.1-followup-evidence.tar.gz -C verified-3.1-evidence
(cd verified-3.1-evidence && sha256sum -c SHA256SUMS)
```

The [archive](jackson-6299-3.1-followup-evidence.tar.gz) contains the complete evidence packet. Its [separate checksum](jackson-6299-3.1-followup-evidence.tar.gz.sha256) verifies that file. `PUBLICATION.json` lists the exact files approved for direct publication and their saved-byte hashes; its own hash is supplied separately with the handoff. The completed final audit and both failed attempts are included. Publication remains the implementation owner's separate action; this packet was prepared locally.
