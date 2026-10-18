# Jackson #6299: independent review of the 3.1 implementation

## Review disposition

**No remaining material implementation change is required by this automated source review before hosted validation.** The EnumSet current-value issue found during the initial pass was corrected and the corrective test was reviewed. This is a source-review conclusion, not a claim that the final implementation has compiled or passed tests.

**Automated review only; no human review is claimed. I did not execute tests or edit Jackson source or tests.** Read-only operations included source/diff inspection, source hashing, checking the unchanged base classes, and `git diff --check` (which reported no whitespace errors). The implementation owner remains responsible for actual baseline/final execution and upstream follow-up.

Review began 2026-10-18 14:20 UTC. Final twelve-file snapshot was read and hashed at approximately 14:24 UTC; the review incorporated the owner's EnumSet correction and both final test files. Any later changed blob needs a corresponding review update.

## Exact source scope

- Upstream 3.1 base: `5d64697ef018099e3d5f951668752ec9d4ffabf9`.
- Local HEAD during review: `4a47e457e7bf8e877bb837693eab58fdc256c22c`, the test-only baseline commit immediately above that base.
- **The implementation was a working-tree change, not part of that HEAD.** The blob/SHA-256 inventory below identifies the exact reviewed implementation and test bytes.
- Reviewed implementation: nine existing Java files plus the new package-private `UnwrappedContainerSerializerHelper.java`.
- Reviewed tests: the amended original `JsonIncludeUnwrappedContainer6299Test.java` and new `JsonIncludeUnwrappedContainer6299CompatibilityTest.java`.
- Related evidence: [branch and fourteen-path audit](JACKSON-6299-BRANCH-REVIEW.md), [predicate/helper design assessment](JACKSON-6299-HELPER-DESIGN-REVIEW.md), and [rejected generator-count design](JACKSON-6299-ENTRY-COUNT-REVIEW.md).

The review was against the actual working source, not the earlier 3.x PR patch. The four inspected shared base files—ArraySerializerBase, AsArraySerializerBase, StdContainerSerializer, and StdSerializer—remained byte-identical to the upstream 3.1 base.

## Resolved finding: EnumSet's current value

The initial probe queried the custom enum serializer before the existing `serializeContents` assignment of `g.assignCurrentValue(value)`. A stable `isEmpty(ctxt, enum)` implementation that reads `ctxt.getGenerator().currentValue()` could therefore observe the parent value during the probe and the EnumSet during serialization. This is separate from an intentionally flipping predicate.

The final probe accepts the generator and assigns the EnumSet as current value before obtaining the enum and resolving/querying its serializer. It preserves the existing raw exception behavior and leaves the retained `serializeContents` dispatch intact. The final EnumSet source blob is `81110d4d0ab0f59208a2e3450a664e7b573e033f`; the initial reviewed blob `23a320d19d74d45f75309f8577512462cd870595` is superseded.

The new `enumEmptinessUsesTheCurrentContainer` test uses a custom enum serializer whose `isEmpty` asserts that current value is an EnumSet, and checks both a suppressed and retained element. That is a meaningful control for the identified change. Its runtime result was not observed by this reviewer.

## Fourteen-route assessment

There are exactly fourteen helper call sites. Each remains inside the original physical-singleton and unwrapping branch; physical multi-element containers still use their existing array path, even if content filtering leaves one survivor.

| Route | Reviewed behavior |
| --- | --- |
| CollectionSerializer | Null handling uses resolved `_suppressNulls`; fixed serializer or cached/dynamic serializer selection matches the original path, including generic specialization. Iterator acquisition and initial `hasNext` remain outside the catch. Fixed-path `next` stays outside its inner catch; dynamic `next` is inside. Probe failures wrap with container and index zero. Existing contents implementation remains unchanged. |
| IndexedListSerializer | Fixed-path `get(0)` remains outside the catch, dynamic lookup inside. Fixed/cached/dynamic generic lookup and null handling match existing behavior. Probe failures wrap with container and index zero. The class is final; no new dispatch change is introduced. |
| EnumSetSerializer | Final probe establishes current value before lookup/predicate. Dynamic lookup uses `getDeclaringClass()`, preserving enum constant-class handling. No new local exception wrapper. Retained contents calls remain as before. |
| IterableSerializer | Fixed/cached/dynamic generic lookup matches the existing path, with null handling before lookup and no local catch. The probe adds a traversal only when filtering is active; the pre-existing singleton detection already requires repeatable traversal. No support for one-shot/changing iterables is claimed. |
| IndexedStringListSerializer | Optimized String predicate receives null serializer, just as its contents path does; null content uses resolved suppression. Probe failures retain container/index wrapping. Original filtered/unfiltered contents selection remains. |
| StringCollectionSerializer | Uses the same feature-and-resolved-state filtering condition as its existing path. Optimized String predicate receives null serializer; null suppression matches. Iterator/predicate failures remain within container/index wrapping. |
| StringArraySerializer | The lazy expression checks null before calling its existing predicate. It passes the actual custom/nondefault element serializer, or null for the optimized String path. Retained fast/slow writes and their distinct null-writing paths are unchanged. |
| ObjectArraySerializer | Null is checked before serializer access. Fixed serializer remains preferred. Dynamic untyped lookup preserves generic specialization; typed lookup uses runtime Class directly, matching this 3.1 branch. Probe failures wrap with the **element** and index zero, matching all three original content paths. |
| BooleanArraySerializer | Boxes as Boolean and reuses the primitive base predicate. Existing boolean emission remains. |
| ShortArraySerializer | Boxes as Short, preserving the existing wrapper-typed suppression comparison and numeric emission. |
| IntArraySerializer | Boxes as Integer. Existing nongated bulk-array path is unchanged. |
| LongArraySerializer | Boxes as Long. Existing typed-primitive behavior and nongated bulk-array path are unchanged. |
| FloatArraySerializer | Boxes as Float and reuses the existing predicate. Separate BINARY representation is unchanged. |
| DoubleArraySerializer | Boxes as Double. Existing bulk-array and separate BINARY paths are unchanged. |

The primitive probes preserve existing NON_DEFAULT equality, including the branch's wrapper semantics. They do not invent an empty primitive value for NON_EMPTY. Reference nulls do not trigger a serializer lookup in the probe. Whole-null-container handling is unchanged because the surrounding serialization dispatch, not these non-null container methods, owns it.

## Shared helper and duplication assessment

`UnwrappedContainerSerializerHelper` is package-private, final, and stateless. It owns the conjunction of filtering enabled and a lazily supplied suppression decision, emits the zero-length array with the original container as current value, and returns whether the caller is done. Disabled filtering short-circuits before invoking the supplier. The helper neither inspects generator counters nor catches exceptions.

This is a substantive extraction of the repeated guard/decision/fallback; it is not merely an extracted three-line write. It addresses the maintainer's request while leaving each family's existing eligibility and serialization dispatch in place.

CollectionSerializer, IndexedListSerializer and IterableSerializer contain similar reference-serializer lookup blocks in their private probes. **I do not recommend another abstraction for those blocks in this patch.** Their fields and dynamic lookup helpers are inherited protected members, and the surrounding element access/catch boundaries differ. A shared utility would need extra state parameters and callbacks, or a new protected helper in a shared base. Either expands coupling/API surface to remove a small amount of lookup code that deliberately follows the existing serializer paths. ObjectArray's typed lookup and EnumSet's declaring-class lookup are further reasons not to force every probe into one resolver.

The new methods are private and the helper package-private. No existing public/protected signatures or contents-method dispatch were replaced. There is no exact-class gate, shared mutable result field, thread-local state, or generator wrapper.

## Scope and behavioral limits

The public container `isEmpty` methods and shared bases were not changed. In particular, this patch does not import the later content-aware property-omission behavior into 3.1. Existing custom String serializer fallback settings and typed StringCollection filtering limitations remain separate.

A retained singleton can have its suppression predicate queried by the probe and again in the normal contents path. As documented in the helper-design review, this is consistent with existing 3.1 query patterns; exactly-once evaluation is not claimed or imposed as a gate. No promise is made for predicates whose answers change during the same serialization or for collections that change between traversals.

The helper adds callback/probe work only in the existing unwrapped singleton branch; its supplier is not evaluated when content filtering is off. This review did not measure allocations or runtime performance.

## Test-source review

The original test class retains its 53 parameterized/focused invocations by source enumeration. It now adds `Reference.getFrom()` identity assertions to the existing five throwing-filter cases, selecting the element for ObjectArray and the container for the other four. This closes a real gap that index/cause assertions alone would miss. The existing WRAP_EXCEPTIONS-off assertions remain.

The original Size.LARGE enum now has a constant class body. This input is useful, but the typed EnumSet<Size> factory normally resolves a fixed element serializer during contextualization, so the class-body change alone must not be reported as proof that the dynamic declaring-class lookup branch executed.

The supplemental compatibility class contributes 21 JUnit invocations by source enumeration:

- Four reference-container families, each internally exercising fixed/nonfixed content serializers and typed/untyped nested object output, including retained output through a filtering delegate and suppressed output.
- Fifteen original container inputs through plain and INCLUDE_ALL filtering delegates, with both root and bean-property output.
- One property-omission control distinguishing physically empty from physically nonempty-but-content-suppressed on 3.1.
- One EnumSet current-value control covering suppressed and retained values.

That gives **74 expected invocations across the two classes**, subject to actual test discovery/execution. Internal loops exercise more scenarios than this invocation count. This review makes no pass/fail claim for them.

The original regression checks retained physical singletons, physically larger inputs reduced to one survivor, explicit wrap/unwrap overrides, null-content/disabled-filter behavior, root output, custom content serialization, JSON/tree consistency, and filter exceptions. The added tests address the concrete generator-context, type/nesting, property-omission, and enum-current-value risks identified during review. They avoid adding a redundant second set of the five exception parameterizations.

## Exact reviewed file inventory

Git blob SHA-1 values below were computed using Git's blob-header/content format. SHA-256 values describe the exact file bytes. These hashes identify the review independently of a later source commit; compare all twelve blobs before reusing this review for that commit.

| Repository-relative path | Git blob SHA-1 | File SHA-256 |
| --- | --- | --- |
| `src/main/java/tools/jackson/databind/ser/jdk/CollectionSerializer.java` | `4c8a462d1ade3622e089190b12a9b6a7e80e7d1f` | `b3247fd0214a7c6e91c5d1331d1aed5a13bb3049e037ea822388682a22f8783e` |
| `src/main/java/tools/jackson/databind/ser/jdk/EnumSetSerializer.java` | `81110d4d0ab0f59208a2e3450a664e7b573e033f` | `6894831d8548c0f149ff099eee4a8ca3ebe8df9d1f28cbe62a1ae4c1127d5ee8` |
| `src/main/java/tools/jackson/databind/ser/jdk/IndexedListSerializer.java` | `11c9d36bb3fc375b7a512f7fc3dd7e240d44a74e` | `c6fd4ce0c7ebf79bc3dc50dfb8280c716374480d48a55bf730de6dc4bf5502da` |
| `src/main/java/tools/jackson/databind/ser/jdk/IndexedStringListSerializer.java` | `05e3b84444bc0f2f4f3c6cfd6145d8342278216f` | `499442ef8827f02350f21896105992c2aeeeddbc2a00224a4200d899dc81b264` |
| `src/main/java/tools/jackson/databind/ser/jdk/IterableSerializer.java` | `85b77ed2a08abe6ba4b949e4bc44e2c5fa95ffc6` | `af95ee291edbf785cf604df1d3d3b24cf2ac0af1f234e192e3e2701aa95791d1` |
| `src/main/java/tools/jackson/databind/ser/jdk/JDKArraySerializers.java` | `66441372973e682e39468e857a48a556be0d6a77` | `6412647a4afec487bcc6ba16a846cacfbd5db7248727f5069d3e1fbcf26f1094` |
| `src/main/java/tools/jackson/databind/ser/jdk/ObjectArraySerializer.java` | `ab7fa5f48b62bd2d5b44ed2c849d70f25b93aa36` | `35a6784d05089c085428c2878cd33e4034be40b7105aa1e824aff751c9930368` |
| `src/main/java/tools/jackson/databind/ser/jdk/StringArraySerializer.java` | `56b0b7984d1e953f5fb98d3e98682a8df879dc92` | `ebe271bb76f188e52cb4f26f1a3dd95150c891145d86924847cd9c15d461ee78` |
| `src/main/java/tools/jackson/databind/ser/jdk/StringCollectionSerializer.java` | `2050dcda8b4e46157032745cbd2c6581a7d6b17e` | `1f4c190e892f6dbea74612f092c25bc7efcec258806685b86a5491a50b4d7a75` |
| `src/main/java/tools/jackson/databind/ser/jdk/UnwrappedContainerSerializerHelper.java` | `8e4320f763ce4cc690cc8bb3fa28256c74bc392a` | `8d8b1beed81c543352166d720dad06c402038e34cec5c47d3c0de926614acfa3` |
| `src/test/java/tools/jackson/databind/ser/filter/JsonIncludeUnwrappedContainer6299CompatibilityTest.java` | `ee003d3222ba219320e43cefd11a54e855be9ebf` | `6c5e13b5926b0d71e84c92e435df8aa184786ab31e8fd2d694813df437921a37` |
| `src/test/java/tools/jackson/databind/ser/filter/JsonIncludeUnwrappedContainer6299Test.java` | `8c40fe94b638eb9bbbdcf9c977545d0b53f758e1` | `d5ba76d4fc261616bc7d62d2cad23294a9d9ae61a95dc49cc4b2b73ba53ca235` |

**Final disposition:** the reviewed twelve-file snapshot is suitable to proceed to the implementation owner's final hosted validation. This document does not certify runtime results, human review, CLA completion, or merge acceptance.

