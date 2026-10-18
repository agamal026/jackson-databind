# Jackson #6299 — completed upstream 3.1 validation

**Upstream workflow attempt 2 passed all four build jobs for source `38261628a27c109e6e49c916cdb0f7e8bf7bbf45`.** Each job executed the original 53 regression tests and 21 compatibility tests, all passing. This supplement records the actual upstream execution for [PR #6302](https://github.com/FasterXML/jackson-databind/pull/6302), separately from the [already published source, review, baseline, and owned-fork validation packet](https://github.com/agamal026/jackson-databind/blob/3e832d88429ddd257b144fb1a811eebb3a3a91e2/contribution-review/6299/3.1-followup/README.md). The original packet and its archive remain unchanged.

The completed [upstream run 39138054220](https://github.com/FasterXML/jackson-databind/actions/runs/39138054220) is the execution evidence. [VALIDATION.json](VALIDATION.json) contains the portable audit and packaging context. This supplement was assembled at 2026-10-18T15:21:22.854531+00:00; publication is the implementation owner's separate action.

## Exact source and checkout

| Role | Exact identity |
| --- | --- |
| Source head | `38261628a27c109e6e49c916cdb0f7e8bf7bbf45` |
| Upstream 3.1 base | `5d64697ef018099e3d5f951668752ec9d4ffabf9` |
| Actual upstream PR merge checkout | `21d5d2915c92b1d3ffdd765567d0b3b2d55e00cf` |
| Source and merge tree | `861d2292ae59d0c91fd3b572cd044212e2580c00` |
| Existing evidence packet commit | `3e832d88429ddd257b144fb1a811eebb3a3a91e2` |

The upstream merge has parents `5d64697ef018099e3d5f951668752ec9d4ffabf9` and `38261628a27c109e6e49c916cdb0f7e8bf7bbf45`. Canonical Git Data and the checkout output in all four full job logs bind execution to that merge. Its tree equals the source tree and the earlier successful owned-fork validation tree. The PR merge checkout is a synthetic CI merge; it does not establish that the pull request was merged into the upstream branch. The PR was observed open and unmerged.

## Actual completed results

Counts are per job. F / E / S means failures, errors, and skipped. The contribution column includes all 53 original regression cases and all 21 compatibility cases; it does not combine repeated matrix executions into a count of distinct tests.

| Upstream job | Observed Temurin Java | Whole-suite tests | Whole-suite F / E / S | Contribution cases passed / run | Android compatibility step |
| --- | --- | ---: | --- | --- | --- |
| [build (17, ubuntu-24.04)](https://github.com/FasterXML/jackson-databind/actions/runs/39138054220/job/117709602754) | 17.0.20+1 | 5883 | 0 / 0 / 1 | 74 / 74 | success |
| [build (21, ubuntu-24.04)](https://github.com/FasterXML/jackson-databind/actions/runs/39138054220/job/117709602933) | 21.0.12+1 | 5885 | 0 / 0 / 1 | 74 / 74 | skipped |
| [build (25, ubuntu-24.04)](https://github.com/FasterXML/jackson-databind/actions/runs/39138054220/job/117709603004) | 25.0.4+1 | 5885 | 0 / 0 / 1 | 74 / 74 | skipped |
| [build (17, windows-latest, W)](https://github.com/FasterXML/jackson-databind/actions/runs/39138054220/job/117709603036) | 17.0.20+101 | 5883 | 0 / 0 / 1 | 74 / 74 | skipped |

Every class total reconciles with the corresponding aggregate. No class failed. The one existing skipped test is in JDKTypeSerializationTest. JDK 21 and 25 additionally execute two passing Java21CollectionsTest cases. The actual Build command is `./mvnw -B -ff -ntp verify`.

Linux JDK 17 also passed `./mvnw -B -q -ff -ntp -DskipTests animal-sniffer:check` between 15:09:44 and 15:09:48 UTC. This is the observed Animal Sniffer Android API compatibility check, not device or emulator execution. The other three Android steps and both downstream-trigger jobs were skipped.

Attempt 2 started at 15:08:01 UTC and the completed run's update is 15:10:36 UTC on 2026-10-18. Metadata was observed at 15:13:35–15:13:36 UTC and all four full logs were retrieved by 15:13:49 UTC. The full audit was finalized at 2026-10-18T15:16:37.592473+00:00.

## Preserved earlier gate observation

At **14:56:13–14:56:43 UTC**, attempt 1 of the same run identifier reported `action_required` and zero actual jobs. At that time upstream build/test execution had not been established. The exact earlier receipt is preserved inside this archive at `history/upstream-observation-1456.json`, SHA-256 `aa832a804ef1848a3127c972622c49d5c4c2fff25f50f61aab5e14775416d02b`.

The later completed **attempt 2** supplies new execution evidence. The earlier receipt remains a truthful historical snapshot and has not been rewritten. This audit does not independently establish who caused or admitted the second attempt. Neither the source reviewer, execution auditor, nor packet assembler triggered or reran the workflow as part of this audit and packaging task.

The current seven check records comprise six GitHub Actions checks corresponding to four successful Build jobs and two skipped downstream jobs, plus the earlier Gitar automated-review check. That bot check is not an additional build or human approval. The saved coverage comment is also from a bot; its percentages are preserved as bot-reported figures, not independently recomputed here. The legacy commit-status endpoint returned an empty collection with a `pending` summary; no pending actual Build job was observed, and the completed workflow/check records establish the execution result above.

## Scope and provenance

This supplement adds upstream CI evidence to the unchanged [original packet](https://github.com/agamal026/jackson-databind/blob/3e832d88429ddd257b144fb1a811eebb3a3a91e2/contribution-review/6299/3.1-followup/README.md). It adds no source, tests, or claims of a human review. Passing CI does not establish maintainer acceptance, a merge, CLA completion, reward eligibility, or earnings. The observed source tree and platforms define the validation scope. The Maven runtime version was not printed; project or plugin versions do not establish it. Snapshot dependency artifact hashes were not captured or pinned.

All four complete connector-returned logs were saved as decoded Unicode text encoded as UTF-8. The supplement preserves the saved bytes, including BOMs and returned LF/CRLF. The Windows log has 1,971 CRLF sequences and zero replacement characters; the Linux logs have LF line endings and zero replacement characters. **Hashes cover saved UTF-8 representations, not original HTTP transport bytes.** Metadata was parsed from public GitHub API responses and saved as JSON; those saved files were copied unchanged. The original auditor receipt is `upstream-hosted/audit.json`, SHA-256 `36f35150b93a0bbb631424f97876a11452e761a394b0696316f73aad472b9fa1`. The top-level VALIDATION document adds packaging context while retaining that audit's fields; the original receipt remains separately available in the archive.

The archive contains the full audit, all four full logs, canonical source/check/run/job/status metadata, the bot coverage comment, the observation metadata, and the historical gate receipt. It also includes a copy of the original packet's publication manifest so its unchanged identities are recorded. Archive-only paths are shown as code; no direct-publication Markdown link depends on an unpublished nested file.

## Download and verify

The direct publication consists of this README, [VALIDATION.json](VALIDATION.json), the [complete archive](jackson-6299-3.1-upstream-evidence.tar.gz), its [checksum](jackson-6299-3.1-upstream-evidence.tar.gz.sha256), and `PUBLICATION.json`. The publication allowlist records exact sizes and hashes; its own hash is supplied separately in the handoff.

Verify the archive, extract into a new directory, then verify every payload file:

```sh
sha256sum -c jackson-6299-3.1-upstream-evidence.tar.gz.sha256
mkdir verified-upstream-3.1
tar -xzf jackson-6299-3.1-upstream-evidence.tar.gz -C verified-upstream-3.1
(cd verified-upstream-3.1 && sha256sum -c SHA256SUMS)
```

`SHA256SUMS` is inside the archive. It covers the README, VALIDATION document, original audit/log/metadata bytes, and historical receipts. It excludes itself, the archive, the archive checksum, and the external publication allowlist. The archive includes the manifest and every covered file. Archive entry ownership and timestamps are normalized; payload bytes are unchanged. The already published packet at `3e832d88429ddd257b144fb1a811eebb3a3a91e2` remains separate and retains its own archive and checksums.
