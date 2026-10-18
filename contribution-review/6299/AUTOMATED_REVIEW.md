# Independent automated source review: Jackson databind #6299

## Decision

**Accept the reviewed source revision. No required source changes remain from this review.**

This is an independent automated source review, not a statement of personal human
review or a legal signature. The reviewer did not implement the source change,
execute tests, or inspect CI results during this final review. Test execution and
hosted platform results belong in the separate validation evidence.

Review completed on **2026-10-18 at 11:49 UTC**. Published source and test content
were independently read back at **11:48:49 UTC**.

## Exact reviewed revision

- Source: [`de533c218b30a86ac9e74a6627b7224c2a0d2987`](https://github.com/agamal026/jackson-databind/commit/de533c218b30a86ac9e74a6627b7224c2a0d2987)
- Tree: `d9550ccd43d7acf13d6e5405728c3f155a53a1df`
- Parent, carrying the exception regression tests: `3da7256ce0a33801c7cc9166304c1fb9ec47f66e`
- Original upstream base reviewed: `d2d5526ee317cbc0300c29e3257a6e9690774d96`
- [Reviewed test source](https://github.com/agamal026/jackson-databind/blob/de533c218b30a86ac9e74a6627b7224c2a0d2987/src/test/java/tools/jackson/databind/ser/filter/JsonIncludeUnwrappedContainer6299Test.java)
- Test SHA-256: `173bca7131ff03859d7d7d55dc80a10824d740467ee45d3ded4c14d33829e185`
- Test Git blob: `fe6aec0b8143469a843a3097918b992391fc82da`

The final commit changes exactly five source files relative to its test-only
parent: 40 added lines and 20 removed lines. Each published blob was compared
with the locally reviewed content. All five matched. The unchanged published
test was separately fetched and hashed; it matched both hashes above.

| Source file under `src/main/java/tools/jackson/databind/ser/jdk/` | Verified final Git blob |
| --- | --- |
| `CollectionSerializer.java` | `1488c0c5566dccc5801bfd72d0729b8ea3246e75` |
| `IndexedListSerializer.java` | `ddc0e0c4766e96a116b645b5248a8533ca38102d` |
| `IndexedStringListSerializer.java` | `e0d25195f1dd605cf6bf556f7c9ef8dd7eb6f295` |
| `ObjectArraySerializer.java` | `7987a90e7f41de5f92b62f97007a6748acc253ab` |
| `StringCollectionSerializer.java` | `f2e6101e34472a907d9ace80383b0b85faeb49f6` |

The earlier implementation at `069a9f6f2a077750428d231b103771fe3795cf4f`
added the singleton guards across nine source files. All nine of that commit's
published source blobs were also independently matched against the reviewed
local files. The final correction retains those guards and changes exception
handling only in the five files listed above.

## Behavior and scope

Fourteen serializer implementations previously selected singleton unwrapping
from physical container size before applying content suppression. A suppressed
sole element could consequently produce no JSON value.

The reviewed change checks content-aware emptiness only inside the existing
effective singleton-unwrapping branch. When the sole element is suppressed, it
writes an empty array with size zero and immediately returns. The immediate
return avoids a second suppression check for that already-suppressed element.

The affected implementations are the six reference-container serializers,
`IterableSerializer`, `EnumSetSerializer`, and the boolean, short, int, long,
float and double array serializers. The review checked their existing `isEmpty`
implementations and shared suppression helpers.

The change preserves physical-size-based unwrapping: two original elements that
filter down to one still produce an array. Property-level format overrides,
disabled content filtering, retained-content dispatch, container type prefixes
and suffixes, and primitive-array fast paths remain in their existing branches.
`IteratorSerializer` remains unchanged and always writes array delimiters.

## Required finding resolved: exception wrapping

The first implementation placed the new emptiness precheck outside the element
exception handling in five serializers. A custom filter or element emptiness
predicate throwing an unchecked exception could therefore bypass
`wrapAndThrow`, changing root serialization from a Jackson exception with an
element index to a raw unchecked exception even with `WRAP_EXCEPTIONS` enabled.

The final correction encloses only the new guard in the existing exception
handling convention and calls `wrapAndThrow(ctxt, e, value, 0)`. That helper
preserves the configured behavior: it wraps the failure with index zero when
enabled and rethrows the unchecked exception when disabled. Existing content
writers remain outside the added catch block, so their failures are not wrapped
a second time by this correction.

The serializers whose content writers do not have this wrapping policy were
left unchanged by the correction. The review accepts the restored exception
type, cause and index behavior; it does not claim byte-identical rendering of
every diagnostic reference description.

## Regression design reviewed

The final test source defines **53 test invocations**, counted statically:
15 container argument rows across three parameterized tests, three ordinary
tests, and five parameterized exception cases. This is a count of defined
invocations, not a claim that this reviewer executed them.

The tests deliberately use value inclusion `ALWAYS`, so content suppression does
not remove the whole property before the affected serializer executes. They
exercise optimized string containers, generic object containers, both reference
array paths, a genuine repeatable `Iterable`, enum filtering, and six primitive
array paths. A string deque supplies an additional collection case.

Assertions cover suppressed and retained singletons, physically larger mixed
containers, property format overrides, null inclusion, disabled filtering, root
output, and custom content serialization. A following bean property checks
generator state. Text output is parsed, and `valueToTree()` output is compared
as JSON text to avoid mistaking Java numeric node-type differences for output
errors.

The five new exception cases use root writers with distinct generic and
optimized type references. `ThrowingFilter.equals(null)` returns false, allowing
the normal contextual null-suppression probe to complete. For non-null content,
the filter deterministically throws `IllegalStateException`. Each case checks
`DatabindException`, path index zero, and the original cause with wrapping
enabled, then checks the raw unchecked exception with wrapping disabled.
The `getPath().get(0).getIndex()` API is already used in the repository's
collection deserialization tests. `DatabindException.wrapWithPath` preserves
the unchecked exception as the cause for this fixture.

## Alternatives and limits

A generator entry-count fallback was considered and rejected during review.
The local `TreeBuildingGenerator` contexts write directly into nodes without
updating the inherited index. Comparing `getEntryCount()` before and after a
successful value could therefore append an unwanted array, overwrite a root
value, or fail while writing a bean. The final implementation has no dependency
on such counters.

Retained elements may still have their inclusion predicate evaluated in both
the emptiness precheck and the existing content writer. This matches an existing
content-aware property-emptiness execution pattern. No exact predicate call
count or support for state-changing keep-then-suppress predicates is claimed.

`IterableSerializer` already assumes that `iterator()` returns a repeatable
view when checking singleton unwrapping. The new precheck uses that existing
assumption. Single-use `Iterator` instances are not subjected to this precheck.

Hosted compilation, before/after failures, full-suite results, and platform
coverage require their actual CI evidence. This source-review acceptance does
not establish a CI pass, an upstream submission, maintainer acceptance, a
financial benefit, or personal human review.
