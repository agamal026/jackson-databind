# Jackson #6299: final helper design assessment

Automated source review, 2026-10-18, following the branch audit and generator-count audit. **No tests were run, no Jackson source was changed, and no human review is claimed.**

## Recommendation

For this focused 3.1 bug fix and the maintainer's shared-helper request, **prefer a lazy suppression probe plus the original serialization path**. A package-private helper in `ser/jdk` should own the filtering guard, lazy probe and empty-array fallback, and return whether the singleton was handled. Call it only from the existing physical-singleton/unwrapping branch. If the value is retained, continue through the original virtual contents method.

This preserves the narrow scope without introducing generator-specific assumptions, exact-class gates, thread-local state, or return-value plumbing through all content loops.

**Correction to the earlier risk framing:** repeated predicate invocation is an observable limitation, but I did not find an exactly-once compatibility guarantee. It should not be promoted into a requirement for #6299. Actual 3.1 source already relies on repeated emptiness and CUSTOM-filter queries. The earlier branch audit's stateful-predicate warning should be read as a limitation to disclose, not a reason to require the larger boolean-return refactor.

The entry-count design remains rejected for the concrete official FilteringGeneratorDelegate counterexample documented in [the separate receipt](JACKSON-6299-ENTRY-COUNT-REVIEW.md).

## Predicate contracts and existing use

### CUSTOM filter equality

[JsonInclude.contentFilter and Include.CUSTOM](https://github.com/FasterXML/jackson-annotations/blob/720da3b9f7dc4e5e650629b034dd47f02837de2e/src/main/java/com/fasterxml/jackson/annotation/JsonInclude.java) define the filter's `equals(value)` result as an inclusion predicate: true suppresses the content; false retains it. The inspected annotation source does not promise one invocation per non-null value across all emptiness and serialization phases. It documents special null-callback behavior, which should not be generalized into an exactly-once rule for all content paths.

[Object.equals in OpenJDK 17 GA](https://github.com/openjdk/jdk/blob/jdk-17-ga/src/java.base/share/classes/java/lang/Object.java) specifies consistent results for repeated comparisons when the compared information has not changed. That supports repeatability for ordinary equality/default suppression. However, Jackson deliberately repurposes equals as a custom filtering hook and permits null-suppression decisions, so the full Object equivalence-relation contract alone is not a sufficient formal argument about all custom filters. The direct Jackson implementation precedent below is stronger evidence.

### ValueSerializer.isEmpty

[ValueSerializer.isEmpty](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ValueSerializer.java), blob `4a3c2077075f85cbcb614f6e0c6fc00d259a1ed5`, documents a query asking whether a serializable value is considered empty for suppression. Its default checks null. It makes no statement guaranteeing a call count, caching, purity, or a single evaluation.

A stable answer for unchanged value/context is consistent with that query contract and with existing use. It would be too strong to claim the documentation explicitly forbids every stateful implementation; it is equally unsupported to promise that a custom implementation is queried exactly once.

### Direct 3.1 precedent

[BeanPropertyWriter](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/BeanPropertyWriter.java), blob `cac45e7400faf297f5bc7218941765e6ebcfc04a`, calls the property's serializer `isEmpty` for NON_EMPTY inclusion and, when retained, calls that serializer's `serialize` or `serializeWithType`.

[MapSerializer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/ser/jdk/MapSerializer.java), blob `9bf1e2610ea5b2b48669a7bbb2b133f7db0a19ca`, already performs content-aware emptiness checks on the exact 3.1 base:

- `isEmpty`, lines 470–519, calls a content serializer's `isEmpty` for NON_EMPTY, or suppression object's `equals` for CUSTOM/default comparison. It handles both fixed and dynamic content serializers.
- A retained map is then serialized.
- Its optional/filtered content paths evaluate the same predicates again before writing; for example, lines 701–711.

Consequently, a nonempty map property configured for property NON_EMPTY and content CUSTOM/NON_EMPTY already queries the relevant retained content twice. This is source-derived behavior, not a test result produced by this reviewer. It demonstrates that exactly-once content-predicate evaluation is not an existing general 3.1 behavior.

### Current 3.x corroboration

[3.x CollectionSerializer](https://github.com/FasterXML/jackson-databind/blob/d2d5526ee317cbc0300c29e3257a6e9690774d96/src/main/java/tools/jackson/databind/ser/jdk/CollectionSerializer.java) calls `_allElementsSuppressed` during its content-aware `isEmpty`. [3.x AsArraySerializerBase](https://github.com/FasterXML/jackson-databind/blob/d2d5526ee317cbc0300c29e3257a6e9690774d96/src/main/java/tools/jackson/databind/ser/std/AsArraySerializerBase.java) implements that traversal and suppression query; normal content serialization later filters again when the property is retained.

This confirms the same design pattern for collections in newer branches. It is evidence for repeated querying, **not** a reason to backport the broader property-omission behavior of content-aware `isEmpty` into 3.1.

## Assessment of the boolean-return alternative

A private contents method returning whether an element passed inclusion and was offered to its existing writer is logically sound for the built-in loops. A single BooleanSupplier invocation followed by fallback avoids both a probe and generator dependence. It should mark retained nulls before early `continue` branches, preserve all original catch blocks and path sources, and return false only when no element was retained. A generator intentionally discarding a retained write or a custom serializer emitting nothing must not be misclassified as databind inclusion suppression.

Its cost is more than changing method signatures privately:

- The six primitive loops and reference/container fixed, typed, dynamic, fast and slow paths all need bookkeeping or delegated return propagation.
- Keeping existing void wrappers preserves signatures but does not automatically preserve virtual dispatch. EnumSetSerializer, IterableSerializer, StringArraySerializer, ObjectArraySerializer and the six primitive serializers are extensible and currently call public overridable contents methods from serialization. ObjectArray also dispatches through overridable public fixed/typed contents methods. Calling new private methods instead bypasses those hooks.
- **Correction to the initial dispatch warning:** IndexedListSerializer is final, so its public contents method is not an external subclass risk. IndexedStringListSerializer is final too. CollectionSerializer already uses private contents implementations internally, and StringCollectionSerializer's contents helpers are private.
- Exact-class gates could preserve old subclass routing, but they add policy and machinery that are unnecessary for the documented #6299 behavior. Thread-local/context-carried result state or generator wrappers would be still larger changes.

There is no evidence-backed reason to pay this complexity solely to promise one predicate invocation. The lazy probe is the narrower maintainable choice for the stated scope.

## Conditions for the narrower patch

1. Centralize the filtering condition and lazy probe in the shared helper. Do not probe when inclusion filtering is disabled. Keep physical-singleton and unwrap eligibility where the current serializer already determines them.
2. Implement 3.1-specific content-suppression queries; do not call that branch's physical container `isEmpty` and expect it to detect suppressed content.
3. Reuse the existing suppression predicates and appropriate serializer selection. Preserve custom serializers, null rules, enum declaring-class handling, and typed-array distinctions. Avoid claiming unchanged invocation count or absolute failure ordering for arbitrary custom callbacks.
4. Keep original content serialization calls and exception semantics. Probe failures in the five wrapped/indexed families need the established path treatment; ObjectArray uses the element as reference source. Raw families should retain their lack of a local wrapper.
5. Retain disabled-filter, retained-singleton, physical-multi-element, explicit-format, custom-content and throwing-filter controls. Include the official FilteringGeneratorDelegate retained-value negative control if testing an implementation that risks generator assumptions.
6. Treat stateful/flipping predicate behavior as outside the guaranteed result of this narrow patch. A call-count test may document behavior, but exactly-once is not a release gate established by the contracts or existing source.
7. Keep property omission and the separately identified 3.1 String-container limitations outside the change.

**Disposition:** recommend the lazy probe helper for implementation review. Actual baseline/final tests, source diff review, and required maintainer/CLA follow-up remain the implementation owner's work.

