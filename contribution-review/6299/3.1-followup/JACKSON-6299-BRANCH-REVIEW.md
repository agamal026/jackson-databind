# Jackson #6299: 3.1 branch and singleton filtering source audit

Observed 2026-10-18, completed within the bounded audit that began at 13:54 UTC.

**This is an automated source review, not human review. I did not execute tests, modify Jackson source, create branches, or publish anything.** Proposed behavior and risks below are conclusions from reading source, not new runtime results. This receipt is the only artifact produced for this audit.

## Decision

Follow-up clarification: [the helper design assessment](JACKSON-6299-HELPER-DESIGN-REVIEW.md) found direct 3.1 precedent for repeated predicate queries. The stateful-predicate risk below does not establish an exactly-once requirement; a lazy probe is the recommended narrow design. The separate generator entry-count proposal was rejected using an official FilteringGeneratorDelegate counterexample.

The oldest affected maintained release line is **3.1**, at inspected commit **5d64697ef018099e3d5f951668752ec9d4ffabf9**. A focused 3.1 implementation should reuse that branch's existing per-element suppression semantics and serializer selection. It should not backport the later content-aware container `isEmpty` implementation: that would also change property omission behavior beyond #6299.

The current PR cannot simply be retargeted with its existing `isEmpty` guards. On 3.1, the relevant container `isEmpty` implementations answer physical emptiness; a physical singleton is not empty even when its content will be suppressed. The 3.x suppression traversal is later functionality, documented `@since 3.2.1`. See the immutable [3.1 AsArraySerializerBase](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/AsArraySerializerBase.java), [3.1 array base](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/ArraySerializerBase.java), and [current reviewed PR head](
https://github.com/FasterXML/jackson-databind/tree/de533c218b30a86ac9e74a6627b7224c2a0d2987).

The narrow behavioral contract is: in the existing physical-singleton/unwrapping branch, a suppressed sole element must produce `[]`; a retained singleton must still unwrap. A physical multi-element container that filters down to one survivor must remain an array. Existing property omission rules must remain unchanged.

## Issue, review requests, and policy

- [Issue #6299](https://github.com/FasterXML/jackson-databind/issues/6299): with container inclusion and singleton unwrapping enabled, a suppressed physical singleton writes no JSON value. The issue describes malformed last-property output and a stream exception when a following property is written.
- [PR #6302](https://github.com/FasterXML/jackson-databind/pull/6302): reviewed submitted head `de533c218b30a86ac9e74a6627b7224c2a0d2987`, submitted base branch 3.x at `d2d5526ee317cbc0300c29e3257a6e9690774d96`.
- [Maintainer branch request](https://github.com/FasterXML/jackson-databind/pull/6302#issuecomment-6250227789): use the oldest maintained branch exhibiting the bug.
- [Maintainer helper request](https://github.com/FasterXML/jackson-databind/pull/6302#discussion_r4307148911): remove the duplication identified at EnumSetSerializer's added guard.
- [Maintainer CLA request](https://github.com/FasterXML/jackson-databind/pull/6302#issuecomment-6250307295).
- [Repository CLAUDE.md at exact 3.1 base](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/CLAUDE.md#branch-strategy), blob `4788043fd441dfcbe933522002194a06aa27297e`, is identical to the inspected 3.x policy file. It directs bug fixes to the oldest affected released branch, describes 3.1 as an LTS maintenance line, and identifies JDK 17 as the development baseline. The general contribution policy separately requires source and binary compatibility for patch releases.
- [General Jackson contribution policy](https://github.com/FasterXML/jackson/blob/main/CONTRIBUTING.md#paperwork), read during this session from the moving main URL: it requires a personally signed CLA before first code acceptance. **The user's CLA status remains unverified; this audit neither signs nor attests to it.** The generic guide's usual 3.x targeting does not supersede this PR's specific maintainer direction and repository branch strategy. No human-review requirement was found in the specific policy examined, and this automated review must not be represented as human review.

### Branch applicability evidence

The feature is absent from each inspected pre-3.1 branch's SerializationFeature source. The 3.1 feature source declares `APPLY_JSON_INCLUDE_FOR_CONTAINERS` with `@since 3.1`. This is direct feature/source evidence, not an assumption that all supported branches share current 3.x behavior.

| Branch | Exact observed head | Feature present |
| --- | --- | --- |
| 2.18 | `3cceef5607f7ac738cca17c6bd4c3dca16fc1940` | No |
| 2.19 | `4ba2c3e5b37a12c9a20e0f6cf22f356b76708911` | No |
| 2.20 | `16ee0edf3a91c34feb7a27486d97d974f45a801b` | No |
| 2.21 | `fe3780f6083a577a378fcb59a3ec5e375b07dc6c` | No |
| 2.22 | `72bb78e16c757c2a15f5f5895e8c874ce448d2d5` | No |
| 2.x | `69ddc80ca06360d58a3c8b55d1712cabbc9aba9b` | No |
| 3.0 | `1a5984c33b4778a53da6664e70b512b9fd2a756a` | No |
| 3.1 | `5d64697ef018099e3d5f951668752ec9d4ffabf9` | Yes |
| 3.2 | `64a48132600eae7bd683dac3546e1ff748936340` | Yes |
| 3.x | `d2d5526ee317cbc0300c29e3257a6e9690774d96` | Yes |

The checked file is `src/main/java/com/fasterxml/jackson/databind/SerializationFeature.java` for 2.x lines and `src/main/java/tools/jackson/databind/SerializationFeature.java` for 3.x lines. Each is addressable through the GitHub blob URL using the exact heads above; [3.1 feature source](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/SerializationFeature.java) and [3.0 source](https://github.com/FasterXML/jackson-databind/blob/1a5984c33b4778a53da6664e70b512b9fd2a756a/src/main/java/tools/jackson/databind/SerializationFeature.java) directly establish the boundary.

The inspected 3.1 feature history records collections support at [867c874e518dcc716fa3d8e1e43f483a4fe618b0](https://github.com/FasterXML/jackson-databind/commit/867c874e518dcc716fa3d8e1e43f483a4fe618b0), from [PR #5370](https://github.com/FasterXML/jackson-databind/pull/5370); the feature rename at [998cb7f60e822bdf264c12547f982c823210597f](https://github.com/FasterXML/jackson-databind/commit/998cb7f60e822bdf264c12547f982c823210597f); and array support at [c0300132d9a418aca36b237d2c85fe690910c528](https://github.com/FasterXML/jackson-databind/commit/c0300132d9a418aca36b237d2c85fe690910c528), corresponding to [issue #5515](https://github.com/FasterXML/jackson-databind/issues/5515). The introduction issues were assigned to 3.1.0.

## The fourteen implementations

All links and line descriptions in this section refer to the exact 3.1 base above. The common filtering condition is resolved suppression state present (`_suppressableValue != null || _suppressNulls`) **and** the container-inclusion feature enabled.

For non-null reference elements, the existing predicate retains everything when suppression value is null; uses serializer `isEmpty` for MARKER_FOR_EMPTY when a serializer is supplied, otherwise String emptiness where applicable; and otherwise negates suppression-value `equals`. Null suppression is handled separately. Preserve those rules, including CUSTOM and NON_DEFAULT resolution.

| Implementation | Existing suppression and serializer selection | Existing local exception handling |
| --- | --- | --- |
| [CollectionSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/CollectionSerializer.java) | Physical size 1. Uses AsArray predicate. Fixed `_elementSerializer` path or dynamic cache lookup; a miss specializes generic element JavaType when needed, otherwise uses Class lookup. Nulls use resolved suppression and default null serializer. Generic Collection handling of EnumSet deliberately disables content TypeSerializer. | Wraps with container and index. Dynamic iteration and fixed iteration have different catch boundaries; fixed-path `next()` is outside its inner catch. |
| [IndexedListSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IndexedListSerializer.java) | Physical list size 1. Fixed, typed dynamic, or ordinary dynamic content paths. Both dynamic paths preserve generic specialization and cache lookup. AsArray predicate and standard null handling. | Wraps with container and index. Dynamic `get(i)` is inside catch; fixed-path `get(i)` precedes its inner try. |
| [EnumSetSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/EnumSetSerializer.java) | Physical size 1. Fixed serializer or `_findAndAddDynamic(ctxt, en.getDeclaringClass())`; do not replace declaring class with runtime class for enum constant bodies. AsArray predicate; no content TypeSerializer and no actual null EnumSet elements. | No local catch/wrap. |
| [IterableSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IterableSerializer.java) | Existing `hasSingleElement` traverses an iterator. Fixed serializer or dynamic Class/cache/generic-specialization path, optional content TypeSerializer; AsArray predicate and standard null handling. Serialization obtains another iterator. | No local catch/wrap. |
| [IndexedStringListSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IndexedStringListSerializer.java) | Physical list size 1; own `_needToCheckFiltering`. StaticList predicate receives **null serializer**, then direct string write. Null values use default null serialization. There is no element serializer lookup in this optimized path. | Wraps with container and index. |
| [StringCollectionSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StringCollectionSerializer.java) | Physical collection size 1; filtering condition inline. StaticList predicate receives **null serializer**, then direct string write. Standard null handling. No element serializer lookup in optimized path. | Wraps with container and index. |
| [StringArraySerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StringArraySerializer.java) | Physical length 1. Own predicate; custom/nondefault `_elementSerializer` supplies `isEmpty` and emission in slow path. Default optimized path supplies null serializer and uses String emptiness. Fast null emission is `g.writeNull()`; slow null emission uses default null serialization. | No local catch/wrap. |
| [ObjectArraySerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/ObjectArraySerializer.java) | Physical length 1. Fixed serializer first; otherwise typed or untyped dynamic path. Untyped dynamic lookup specializes generic JavaType; **typed dynamic lookup uses Class only** in this base. Own predicate, resolved serializer and standard null handling. | All three paths wrap with **element**, not whole array, plus index: lines 321, 356, 390. |
| BooleanArraySerializer in [JDKArraySerializers](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/JDKArraySerializers.java) | Box as Boolean for primitive base predicate; direct boolean emission. | No local catch/wrap. |
| ShortArraySerializer in JDKArraySerializers | Box as Short; direct numeric emission with existing int conversion. | No local catch/wrap. |
| IntArraySerializer in JDKArraySerializers | Box as Integer; direct numeric emission. Preserve nongated larger-array fast path. | No local catch/wrap. |
| LongArraySerializer in JDKArraySerializers | Box as Long; direct numeric emission; typed primitive base. Preserve larger-array fast path. | No local catch/wrap. |
| FloatArraySerializer in JDKArraySerializers | Box as Float; direct numeric emission. BINARY shape can select a different serializer and is outside this path. | No local catch/wrap. |
| DoubleArraySerializer in JDKArraySerializers | Box as Double; direct numeric emission. Preserve larger-array fast path and separate BINARY shape. | No local catch/wrap. |

The [primitive base predicate](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/ArraySerializerBase.java) treats MARKER_FOR_EMPTY as retain: primitive elements have no empty value under this predicate. NON_DEFAULT uses the already resolved wrapper-typed default and equality, not newly invented numeric rules. NON_NULL cannot remove actual primitive elements.

## Shared helper placement and design boundary

**The smallest placement that can serve all fourteen implementations without adding public/protected API is a package-private utility in `ser/jdk`.** All fourteen concrete implementations live there. StaticListSerializerBase extends StdSerializer directly, so adding one helper only to StdContainerSerializer does not cover the optimized String containers. Changing that inheritance or adding abstract protected hooks would enlarge compatibility risk.

The helper should own the filtering guard, lazily invoke the suppression decision, write the empty array, and return whether it handled the value. Merely receiving an already calculated `suppressed` boolean and writing three lines would leave the repeated guard structure in every caller. A design sketch, not an implemented or compiled API:

```java
static boolean serializeSuppressedSingleton(
        Object value, JsonGenerator g, boolean filtering,
        BooleanSupplier isSuppressed) {
    if (!filtering || !isSuppressed.getAsBoolean()) {
        return false;
    }
    g.writeStartArray(value, 0);
    g.writeEndArray();
    return true;
}
```

Call this only inside each serializer's existing physical-singleton and unwrapping branch. Pass the existing family's filtering decision, preserving overrides where present; pass a lazy suppression operation so disabled filtering never obtains an extra iterator, resolves an element serializer, or invokes custom filtering. The helper owns the repeated conjunction and decision. It must not catch exceptions itself. The caller or predicate adapter preserves the class-specific boundaries in the table.

A suppression adapter must reuse the exact existing lookup path, including custom serializers, dynamic cache, generic specialization, declaring-class enum lookup, typed-array distinctions, and null decisions. Looking up a serializer only for NON_EMPTY, while skipping lookup for CUSTOM equality, would change failure ordering compared with the current serialization loop. Do not assume that optimization is equivalent.

**Material design limitation:** probing suppression and then calling the original filtered contents method evaluates the predicate again for a retained singleton. A stateful CUSTOM filter or custom serializer `isEmpty` could return different answers and still leave no emitted value; it can also observe a changed call count. Therefore, this minimal helper is a viable structural direction for ordinary stable predicates, but it is **not proven sufficient for all custom behavior**. The implementation must make an explicit, tested decision here. A stricter helper would own one suppression decision and both outcomes, using the already resolved serializer for an unfiltered retained-element write; that requires preserving each family's existing emission/type/null behavior and is a somewhat larger change. Do not claim complete custom-filter correctness from the small sketch alone.

Capturing callbacks may allocate on the opted-in singleton path; allocation or performance effects were not measured. A family-local private helper alternative avoids a new utility but leaves multiple implementations and should be judged against the maintainer's actual duplication request. Do not change public `serializeContents` signatures, add required abstract methods, or catch all failures centrally merely to simplify this helper.

## Compatibility findings and scope exclusions

1. **Object-array error source is a required compatibility detail.** The submitted 3.x guard wraps a probe failure using the whole array; the 3.1 contents paths use the failing element. Preserving only index zero, exception type, and cause is insufficient. A 3.1 revision should preserve `DatabindException.Reference.getFrom()` as well. The submitted five throwing-filter parameterizations assert index and cause but do not assert this source object.
2. **Iterator boundaries matter.** An added suppression probe can introduce a further traversal beyond IterableSerializer's existing repeated traversal. A one-shot or changing Iterable is not newly supported merely because a repeatable test Iterable passes. Avoid broad new wrapping of iterator acquisition, `hasNext`, or `next` that changes existing raw exceptions.
3. **Property omission is outside #6299.** Do not import the content-aware container `isEmpty` changes associated with [#6065](https://github.com/FasterXML/jackson-databind/issues/6065). On this branch a physically nonempty container can remain present even when all its content is suppressed.
4. **Static String custom serializer fallback is already incomplete.** In [StaticListSerializerBase.createContextual](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StaticListSerializerBase.java), the default String path carries resolved inclusion/unwrap settings, but a custom content serializer returns a fresh CollectionSerializer without carrying those resolved settings. Treat this as a separate existing issue, not an automatically solved #6299 path.
5. **StringCollectionSerializer's typed path is already different.** Its 3.1 `serializeWithType` calls the unfiltered contents method. IndexedStringListSerializer's typed path does check filtering. Do not silently fold typed String-collection filtering into this fix.
6. Binary byte arrays, character arrays, BINARY Float/Double arrays, maps, entries, reference wrappers, IteratorSerializer and third-party datatypes are not established as the same fourteen affected paths. Broadening to them needs separate evidence.

## Meaningful verification required from the implementation owner

No tests below were run by this reviewer. The submitted regression source provides a starting point, not evidence that a new 3.1 design has passed.

- Reproduce the defect on the exact 3.1 base before implementation, using that branch's own build/dependencies. Test the final changed source separately.
- Retain coverage across all fourteen concrete implementations, with suppressed singleton, retained singleton, and physical multi-element input that leaves one survivor.
- Keep root, property-followed-by-another-property, explicit unwrap/wrap overrides, disabled filtering, and null-content controls. Add last-property/output parseability when it provides a distinct observable failure.
- Exercise fixed custom content serializers and dynamic serializers, NON_EMPTY, CUSTOM, NON_NULL and primitive NON_DEFAULT as applicable. A typed generic Object array and an enum constant with a class body guard the lookup distinctions recorded above.
- Preserve throwing-filter behavior with WRAP_EXCEPTIONS on and off, **including reference source identity as well as index and cause**, for the indexed/wrapped families. Raw families should retain their current lack of a local wrapper.
- If the design prechecks suppression, use an invocation-count or stateful predicate control to expose repeated evaluation. Resolve that concrete risk rather than reporting broad success from stable filters alone.
- Keep an explicit property-omission control proving that the fix has not imported later content-aware `isEmpty` behavior. Do not expect unrelated custom String or typed String-collection limitations to become fixed implicitly.

## Immutable reviewed source inventory

The source tree was retrieved untruncated at the exact 3.1 commit. These blob identities let the implementation owner determine whether this audit still applies after any source update.

| Path relative to src/main/java/tools/jackson/databind | Exact blob |
| --- | --- |
| [ser/jdk/CollectionSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/CollectionSerializer.java) | `424f7448a3ded32c387acedb01a862d5434697e1` |
| [ser/jdk/IndexedListSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IndexedListSerializer.java) | `d3a7a94835ef7ebd56c48f0a7b86c41f0184da11` |
| [ser/jdk/EnumSetSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/EnumSetSerializer.java) | `f52b9f5e3140402985414fad00fd5ba331064c81` |
| [ser/jdk/IterableSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IterableSerializer.java) | `d7914b20dbbd186295b5b474b1dc8a69217bc47f` |
| [ser/jdk/IndexedStringListSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/IndexedStringListSerializer.java) | `f21e880d285181ccda9dcaae01430ecbd14993a1` |
| [ser/jdk/StringCollectionSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StringCollectionSerializer.java) | `6fb4bc565cecd01d0fec8c3a55a58d7f3d092a3d` |
| [ser/jdk/StringArraySerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StringArraySerializer.java) | `fe7a83bac0528cf02a5651c680149990615ac36d` |
| [ser/jdk/ObjectArraySerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/ObjectArraySerializer.java) | `64c9988fea02372ca22e168a4c0912eeff833e41` |
| [ser/jdk/JDKArraySerializers.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/JDKArraySerializers.java) | `395574d2a86a4ded3832d23418ce6ac7cddb79fd` |
| [ser/jdk/StaticListSerializerBase.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/StaticListSerializerBase.java) | `a3850c9ca6e7578d885f37d5d9ed346c78bdb33f` |
| [ser/std/AsArraySerializerBase.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/AsArraySerializerBase.java) | `404db72e1700c0647e206fd547e309712c98ce48` |
| [ser/std/ArraySerializerBase.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/ArraySerializerBase.java) | `eaee5c64e17e3d33525282522141f132723088b6` |
| [ser/std/StdSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/StdSerializer.java) | `8f8812e9b56264274ed564fa0aed91ab6372387d` |
| [ser/std/StdContainerSerializer.java](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/std/StdContainerSerializer.java) | `d11e43def567d28f749988d240bf6d2fbe85ef91` |

Additional reviewed sources: 3.1 SerializationFeature, branch feature files and history, issue #6299, PR metadata and review comments, submitted regression test and patches, CLAUDE.md, and the general contribution policy as identified above.

**Disposition:** the branch decision is established and the fourteen filtering/lookup paths are mapped. No implementation is approved by this document: the shared-helper choice, exact exception compatibility, and repeated-predicate risk still require implementation review and actual 3.1 baseline/final execution. Human review and CLA completion are not claimed.

