# Jackson #6299: 3.1 test API correction and source binding

Publication copy: only this notice and the display of an archive-only evidence link differ from the original receipt. The original `JACKSON-6299-API-CORRECTION-REVIEW.md` is preserved unchanged inside the archive, SHA-256 `8d1f1bb678b728cafd5e54c90a13f2988d0f3ec6c659fbdea1d0d92fad0ae51c`. Extract the archive before opening its evidence paths. This review records its earlier source-inspection state; [VALIDATION.json](VALIDATION.json) gives the latest runtime results.

**Automated source review only; no human review and no test execution by this reviewer.** Reviewed at 2026-10-18T14:40:18.185200+00:00.

The preserved [original source review](JACKSON-6299-3.1-SOURCE-REVIEW.md) identified the twelve-file snapshot later committed as `c2366f76b7a3e1a2c707acb4e25df2e1cd3a6a00`, tree `93ddd45b71b193a9305ebe1d87e690998192e054`. All twelve recorded blobs and SHA-256 values were independently compared with that commit and matched. That review did not compile or run tests and missed an unavailable test API: `Reference.getFrom()`.

The first hosted attempt (`final-hosted/attempt-c2366f-failure/audit.json` in the archive) subsequently failed test compilation on all four jobs at `JsonIncludeUnwrappedContainer6299Test.java:267:62`. No tests ran in that attempt. Its prior expectation of 74 invocations was source enumeration, not test discovery or a passing count.

## Exact correction

Corrected commit: [`60218d2e760de528fdc48748c8a844a34f3755c8`](https://github.com/agamal026/jackson-databind/commit/60218d2e760de528fdc48748c8a844a34f3755c8). Tree: `2eeb7de459109da30ccea323373d1f6c47ceba71`. Parent: `c2366f76b7a3e1a2c707acb4e25df2e1cd3a6a00`.

The complete parent-to-correction diff changes one line in one test: `getFrom()` becomes `from()`. See [api-correction.patch](api-correction.patch). Direct comparison of the exact Git blobs established that this is the only token change. The other eleven reviewed source/test blobs, including every production file and the compatibility test, are unchanged.

The corrected assertion still checks that the first wrapped exception reference has the expected source identity: the element for ObjectArray, the container for the other four families. The index, cause, and WRAP_EXCEPTIONS-off assertions remain unchanged. The change therefore repairs API spelling without weakening the intended control.

The official pinned [Jackson core Reference source](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/JacksonException.java) was independently read. It exposes `public Object from()`, returning the stored reference source, and has no `getFrom()` accessor. Its documentation explains that this accessor intentionally does not use getter naming. This source inspection establishes the appropriate API; it does not establish that any unpinned snapshot dependency resolved in CI is byte-identical to this source commit.

| Corrected file | Exact identity |
| --- | --- |
| Repository-relative path | `src/test/java/tools/jackson/databind/ser/filter/JsonIncludeUnwrappedContainer6299Test.java` |
| Git blob SHA-1 | `9c990a0286dbe0ba5a4a88956552c36c9656d1f5` |
| SHA-256 | `632b0aadca97a90500e5cea1a3153cde310dd0bf38ff24adf4f2a27a6d7e2f60` |
| Bytes | 13135 |

[The source manifest](SOURCE-IDENTITIES.json) binds every original reviewed blob and every corrected-source blob. The original review documents remain unchanged as historical records. This correction review is suitable to accompany the corrected source into hosted validation; final runtime results belong to [VALIDATION.json](VALIDATION.json), and remain pending until the replacement audit is complete. No compilation, passing-test, human-review, CLA-completion, upstream-acceptance, or earnings claim is made by this review.
