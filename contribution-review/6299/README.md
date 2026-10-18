# Jackson databind #6299: source and validation evidence

The final change emits `[]` when content filtering removes the sole element of a container whose single-element array unwrapping is enabled. It also preserves the affected serializers' configured exception wrapping when the new emptiness check throws.

[Issue #6299](https://github.com/FasterXML/jackson-databind/issues/6299) · [Final source](https://github.com/agamal026/jackson-databind/commit/de533c218b30a86ac9e74a6627b7224c2a0d2987) · [Download the complete evidence ZIP](jackson-databind-6299-evidence.zip) · [Validation record](VALIDATION.json) · [Independent automated review](AUTOMATED_REVIEW.md)

## Exact source

- Upstream base: `d2d5526ee317cbc0300c29e3257a6e9690774d96`
- Final source head: `de533c218b30a86ac9e74a6627b7224c2a0d2987`
- Final tree: `d9550ccd43d7acf13d6e5405728c3f155a53a1df`
- Actual final CI merge checkout: `ffdc7267fb85cc13bdf946840a2623ab898e0d35`
- Test SHA-256: `173bca7131ff03859d7d7d55dc80a10824d740467ee45d3ded4c14d33829e185`

The archive includes the complete ten-file diff against the upstream base: nine production files and one regression-test file, with 394 added lines and no removed lines. `source/SOURCE_FILES.json` records each final file's SHA-256 and Git blob ID, plus the test hashes at all three stages. These hashes were generated from the clean final source checkout and matched with Git object content. The separate validation record records the coordinating agent's canonical Git Data verification of the final and baseline merge trees and parents.

## Before and after

Counts below are **tests / failures / errors / skipped**. A full-suite test total includes its one skipped test; it is not a claim that every listed test executed successfully.

| Stage | Hosted job | New regression class | Full suite |
| --- | --- | --- | --- |
| Baseline | [build (17, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124036186/job/117665313783) | 48 / 1 / 32 / 0 | 7569 / 1 / 32 / 1 |
| Baseline | [build (17, windows-latest, W)](https://github.com/agamal026/jackson-databind/actions/runs/39124036186/job/117665313857) | 48 / 1 / 32 / 0 | 7569 / 1 / 32 / 1 |
| Baseline | [build (21, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124036186/job/117665313618) | 48 / 1 / 32 / 0 | 7571 / 1 / 32 / 1 |
| Baseline | [build (25, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124036186/job/117665313774) | 48 / 1 / 32 / 0 | 7571 / 1 / 32 / 1 |
| Exception baseline | [JDK 21, Ubuntu](https://github.com/agamal026/jackson-databind/actions/runs/39124541594/job/117666801298) | 53 / 5 / 0 / 0 | 7576 / 5 / 0 / 1 |
| Final | [build (17, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124679661/job/117667201285) | 53 / 0 / 0 / 0 | 7574 / 0 / 0 / 1 |
| Final | [build (17, windows-latest, W)](https://github.com/agamal026/jackson-databind/actions/runs/39124679661/job/117667201282) | 53 / 0 / 0 / 0 | 7574 / 0 / 0 / 1 |
| Final | [build (21, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124679661/job/117667201332) | 53 / 0 / 0 / 0 | 7576 / 0 / 0 / 1 |
| Final | [build (25, ubuntu-24.04)](https://github.com/agamal026/jackson-databind/actions/runs/39124679661/job/117667201103) | 53 / 0 / 0 / 0 | 7576 / 0 / 0 / 1 |

The corrected baseline at `342f5dbae53a7b44ec7327fd6def30808f8a1047` contains the first 48 regression invocations without the fix. Each of four hosted jobs records one assertion failure and 32 errors in the new test class. The root-container case emits no value instead of `[]`; bean cases encounter an invalid generator state. The other 15 invocations pass.

The exception baseline at `3da7256ce0a33801c7cc9166304c1fb9ec47f66e` contains the initial fix and five additional exception regressions. Its JDK 21 log was parsed directly: all five failures expected `DatabindException` but received `IllegalStateException`, while the preceding 48 invocations pass. This second baseline isolates the correction requested by independent automated review.

The final revision passes all 53 regression invocations on all four platforms. Every per-class summary reconciles exactly to its job aggregate, with no failing or errored class. JDK 17 reports 1,080 classes; JDK 21/25 report 1,081, including the two-test `Java21CollectionsTest`. The one full-suite skip belongs to `JDKTypeSerializationTest`.

The final Linux JDK 17 job also records a successful **Android SDK compatibility** step using `animal-sniffer:check`. This is static API compatibility checking, not execution on an Android device or emulator. Other matrix jobs skip that step.

## Design and regression scope

The content-aware emptiness guard runs only inside the existing effective singleton-unwrapping branch. A suppressed singleton writes an empty array and returns immediately. A retained singleton follows the existing content writer. Containers with two physical elements that filter down to one remain arrays.

The change covers six reference-container serializers, `IterableSerializer`, `EnumSetSerializer`, and the boolean, short, int, long, float and double array serializers: fourteen implementations across nine production files. Five reference serializers wrap exceptions from the new guard using their existing `wrapAndThrow` convention. The regression checks verify the Jackson exception, original cause and index zero with wrapping enabled, and the raw unchecked exception with wrapping disabled.

Tests also cover suppressed and retained singletons, physically larger mixed containers, property-level format overrides, null inclusion, disabled filtering, root output and custom content serialization. A following bean property tests generator state; parsed text and `valueToTree()` output check both output paths. The independent review explains serializer dispatch, the rejected generator-counter fallback, and the existing repeatable-Iterable assumption.

Retained elements may have their inclusion predicate evaluated by both the emptiness precheck and the content writer. No exact predicate-call count or support for state-changing keep-then-suppress predicates is claimed. Iterator serialization is unchanged.

## Reproduction

Use JDK 17, 21 or 25 with access to the project's Maven repositories. The following commands reproduce the focused test for each recorded source stage; the first two commands that run tests are expected to fail with the counts above.

```sh
git clone https://github.com/agamal026/jackson-databind.git
cd jackson-databind

git switch --detach 342f5dbae53a7b44ec7327fd6def30808f8a1047
./mvnw -B -ff -ntp -Dtest=JsonIncludeUnwrappedContainer6299Test test

git switch --detach 3da7256ce0a33801c7cc9166304c1fb9ec47f66e
./mvnw -B -ff -ntp -Dtest=JsonIncludeUnwrappedContainer6299Test test

git switch --detach de533c218b30a86ac9e74a6627b7224c2a0d2987
./mvnw -B -ff -ntp -Dtest=JsonIncludeUnwrappedContainer6299Test test
./mvnw -B -ff -ntp verify
```

On Linux with JDK 17, reproduce the compatibility step after the successful build:

```sh
./mvnw -B -q -ff -ntp -DskipTests animal-sniffer:check
```

The recorded hosted command for the complete suite was `./mvnw -B -ff -ntp verify`. Java setup reports Temurin 17.0.20+1, 21.0.12+1 and 25.0.4+1 on Ubuntu 24.04.5 LTS, and Temurin 17.0.20+101 on Windows Server 2025. The logs do not print the Maven runtime version. Maven snapshot dependency artifact hashes were not pinned or captured, so source hashes alone do not guarantee byte-identical dependency resolution on a later run.

## Evidence contents and limits

The ZIP contains the complete decoded logs and job metadata for the corrected baseline and final four-job matrices, their audit records, the exception-baseline log and parsed audit, the source patch and source-file hash manifest, this README, the validation record and the unchanged independent automated review. `SHA256SUMS` hashes every other entry; it excludes itself. Archive paths are checked against an explicit allowlist.

Logs retain the decoded content provided by the GitHub connector. Original HTTP transport bytes are unavailable. The exception-baseline log was normalized to LF. Baseline Windows replacement characters were already present in the tool-decoded response. A local build encountered dependency-host DNS resolution failure; its raw local log and private paths are excluded.

Implementation was AI-assisted. A separate automated reviewer inspected the source, identified the exception-wrapping problem, and accepted the corrected exact revision. Another automated audit reconciled the hosted logs. These roles provide independent automated checks; they do not constitute personal human review. This packet establishes the recorded source and validation results, and makes no claim of upstream submission, maintainer acceptance, reward eligibility or payment, or CLA acceptance.
