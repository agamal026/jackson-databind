# Jackson #6299: independent automated compatibility-fixture review

**Result: no required changes found within this review scope. Execution remains unverified for these new bytes.**

Reviewed on 2026-10-18, beginning at 14:44:21 UTC and recorded at 2026-10-18T14:45:15.475418+00:00. This is an independent automated source review, not human review or an execution result.

## Exact reviewed scope

The review compares the working-tree compatibility fixture against source commit **60218d2e760de528fdc48748c8a844a34f3755c8**. Only the following file differs:

- File: src/test/java/tools/jackson/databind/ser/filter/JsonIncludeUnwrappedContainer6299CompatibilityTest.java
- New bytes: **11217**
- New SHA256: **047587024422732e8e26ca6fde2aa0c77ccc511f506c0c81ec42316525da404e**
- New Git blob: **306f9d7f1eb472a13784ab1d43ed6976b4cb8642**
- Previous Git blob: **ee003d3222ba219320e43cefd11a54e855be9ebf**

The original 53-case regression file and all ten production files are unchanged in this comparison. The fixture method is now named **singletonUnwrappingPreservesCustomContent**. Four container inputs, fixed and dynamic content serializers, typed and untyped inputs, retained and empty values, and delegate comparisons remain exercised by the source.

## Why the revised control is appropriate

The fixture captures actual unwrapped serialization before producing its wrapped reference. This ordering preserves first-use serializer resolution in the changed path; generating the control first could warm the shared serializer cache.

The reference uses the same writer with WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED disabled. The fixture Bean has no property-level unwrapping override. The reference therefore exercises the wrapped path while retaining the same content serializer, inclusion and type configuration. It requires an array with at most one element, preserves an empty array when all content is suppressed, and extracts only a sole retained element for the expected unwrapped shape.

The control is constrained by absolute assertions: a retained value must produce exactly one element with nested.text equal to keep; untyped empty content must be suppressed. The complete enclosing JSON, including the after property, is compared for direct output and the filtering-generator delegate. The original 53 explicit expected-output and exception cases remain unchanged.

At upstream 3.1 base **5d64697ef018099e3d5f951668752ec9d4ffabf9**, the wrapped paths in [ObjectArraySerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/ObjectArraySerializer.java) and [IterableSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IterableSerializer.java) use the existing serializeContents implementations. The production patch adds the suppression helpers inside the singleton-unwrapped branches. The wrapped control thus checks preservation of existing content behavior while allowing only the intended outer-shape difference.

## Failure context and limits

[Hosted run 39136668969](https://github.com/agamal026/jackson-databind/actions/runs/39136668969), on the prior fixture, passed all 53 original cases but failed two of the 21 compatibility cases on every runner. The failures concerned object-array typed empty content and an iterable type-tag expectation. That failed attempt is preserved separately.

No unchanged-3.1 execution of those two compatibility inputs was performed by this reviewer. This review does not label either difference an independently reproduced preexisting bug and does not establish its precise runtime cause. It replaces assumptions about universal type-tag and typed-empty behavior with a constrained, same-run wrapped control; it does not widen production behavior.

The reviewer read source, diff and recorded evidence only. No build, test run, CI rerun, source edit, upstream write or publication was performed for this review. The forthcoming hosted matrix must establish whether these exact bytes compile and pass. No human approval, maintainer acceptance, merge or reward eligibility is claimed.
