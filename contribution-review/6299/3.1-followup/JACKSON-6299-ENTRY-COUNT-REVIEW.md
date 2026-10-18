# Jackson #6299: generator entry-count helper review

Automated source review, 2026-10-18, begun 14:06 UTC. **No tests were executed and no Jackson source was edited.** The counterexample below is established by tracing inspected source; it is not represented as a runtime reproduction.

## Decision: reject the generic helper

The proposed helper would retain `g.streamWriteContext()` and its `getEntryCount()`, invoke the existing singleton contents method once, and emit an empty array if the retained context count stayed equal. It has attractive properties for ordinary JSON generators: no duplicate content filtering, serializer lookup, iterator traversal, or new exception wrapping.

**It is not compatible with the public generator abstraction.** An official jackson-core generator, FilteringGeneratorDelegate, writes retained values without consistently advancing the context count it exposes. Consequently, unchanged count does not prove that the contents method wrote no value. Do not use this helper across the fourteen serializers.

The APIs themselves are public. The unsupported assumption is that every generator exposes a stable mutable context whose entry count advances exactly once for each successfully attempted value. The implementation evidence contradicts that assumption.

## Exact source scope

- jackson-databind 3.1: `5d64697ef018099e3d5f951668752ec9d4ffabf9`.
- jackson-core 3.1, branch head observed in this review: `ca96e4a370578857d15f51cf9cd76a8caa22a0f6`.
- jackson-dataformats-text 3.1: `e2b0d13413b80a80b26c60637e1b94d4744ef4f7`.
- jackson-dataformats-binary 3.1: `c9aedc5bcbcf8922854a60bb10a5e303c32477e0`.

The dataformat sources were inspected as representative contract checks, not a complete format compatibility audit.

## Official counterexample

[FilteringGeneratorDelegate](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/filter/FilteringGeneratorDelegate.java), blob `1ced3f80c3586aaf4c8d5aad897f72159ce20754`:

1. Its `streamWriteContext()` returns its own `_filterContext` (TokenFilterContext), not the underlying JSON generator's write context.
2. `writeString(String)` calls `_filterContext.checkValue(...)` only when `_itemFilter != TokenFilter.INCLUDE_ALL`.
3. With INCLUDE_ALL, it calls `delegate.writeString(value)` directly. The exposed context's count stays unchanged.
4. The same bypass occurs for other scalar writes and the INCLUDE_ALL structural-start branches.

[TokenFilterContext](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/filter/TokenFilterContext.java), blob `d3da13c1e74b5e4cf84fad8351a78feee758ac9d`, adds another failure: `checkValue` returns immediately for object contexts without incrementing `_index`. `setPropertyName` also does not increment it. Thus object properties are not tracked as a universal emitted-value counter even outside the INCLUDE_ALL shortcut.

A concrete source-derived negative control is a retained `List<String>` singleton, container inclusion active with NON_EMPTY, and singleton unwrapping enabled, serialized through a FilteringGeneratorDelegate configured with INCLUDE_ALL:

- Before `serializeContents`, the exposed root context count is zero.
- `writeString("keep")` reaches the underlying generator and writes the scalar.
- The exposed root count remains zero.
- The proposed helper writes an additional empty array. An ordinary underlying JSON root accepts a second root value; output now represents two values instead of one. In an object-property context, the underlying generator has already consumed the value position, so the extra array start can throw for a missing property name. In an array context, it can add a second array element.

The consequence follows from the inspected methods. The exact output and exception should be verified if this negative control is executed later.

Recursively inspecting the delegate's counter is not a general repair: generator-level filtering can intentionally discard a value that passed databind inclusion. Treating that discard as databind suppression and offering a new empty-array value changes filter decisions, position counts, or output. A special case for FilteringGeneratorDelegate would also leave other formats and custom generators unresolved.

## Where the count approach does work by source inspection

[TokenStreamContext](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/TokenStreamContext.java) documents `getEntryCount()` as the number of complete and started entries and implements it as `_index + 1`. [JsonGenerator](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/JsonGenerator.java) describes `streamWriteContext()` as logical output position; it does not define this method as a write-observation or suppression-detection API.

[JsonWriteContext](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/json/JsonWriteContext.java), blob `4e696e9515bffad19044345dbe6981432eb2ebfe`, increments `_index` when `writeValue()` accepts a value in object, array, and root contexts. Writing an object property name alone does not increment it. [JsonGeneratorBase](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/json/JsonGeneratorBase.java) returns its live context. The inspected [WriterBasedJsonGenerator](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/json/WriterBasedJsonGenerator.java) calls value verification before scalar values and before switching to a child context for an array/object start.

For those ordinary JSON paths:

- Root, object-value, and array-element positions distinguish zero writes from one write.
- A retained nested object/array advances the saved parent before opening its child. Keeping the parent reference, rather than checking only the final active child, is the correct comparison.
- An empty retained object or array still counts as a value.
- Pretty printing does not remove the logical context transition.
- A serializer exception exits before fallback if the helper does not catch it or use a finally block. It must not append recovery output after an exception.
- Equality comparison is robust to a single signed-int wrap: one increment never leaves the same int value. More than one full 2^32 cycle could compare equal, but that is not a valid one-value singleton serialization. Inequality/order comparisons should not be substituted casually.
- `writeRawValue` updates value state, while `writeRaw` explicitly bypasses normal value/separator handling. A custom serializer using only raw writes cannot be classified by this count. Such raw-write behavior is not proof of a well-formed serializer, but the helper would change it.

[JsonGeneratorDelegate](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/util/JsonGeneratorDelegate.java), blob `1547fe841e2011b5e74edd6a0083a1dc8d291f5d`, normally delegates both context access and writes, so a plain forwarding delegate over the JSON generator preserves this behavior. Its subclass FilteringGeneratorDelegate demonstrates why that observation cannot be generalized to all delegates.

## Other generator and format observations

[SimpleStreamWriteContext](https://github.com/FasterXML/jackson-core/blob/ca96e4a370578857d15f51cf9cd76a8caa22a0f6/src/main/java/tools/jackson/core/util/SimpleStreamWriteContext.java) supplies value counting for the inspected databind [TokenBuffer](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/util/TokenBuffer.java). TokenBuffer's scalar append and structural-start append methods call `writeValue()`. The inspected [TreeBuildingGenerator](https://github.com/FasterXML/jackson-databind/blob/5d64697ef018099e3d5f951668752ec9d4ffabf9/src/main/java/tools/jackson/databind/node/TreeBuildingGenerator.java) also exposes its SimpleStreamWriteContext. This supports ordinary buffer/tree-path analysis but does not establish a universal generator contract.

The inspected [YAMLGenerator](https://github.com/FasterXML/jackson-dataformats-text/blob/e2b0d13413b80a80b26c60637e1b94d4744ef4f7/yaml/src/main/java/tools/jackson/dataformat/yaml/YAMLGenerator.java) and [CsvGenerator](https://github.com/FasterXML/jackson-dataformats-text/blob/e2b0d13413b80a80b26c60637e1b94d4744ef4f7/csv/src/main/java/tools/jackson/dataformat/csv/CsvGenerator.java) expose SimpleStreamWriteContext and use its value transitions. Format-specific restrictions still matter: for example, CSV array-valued properties can require array separator schema configuration. This review does not claim all fallback values are accepted by all schemas.

[CBORWriteContext](https://github.com/FasterXML/jackson-dataformats-binary/blob/c9aedc5bcbcf8922854a60bb10a5e303c32477e0/cbor/src/main/java/tools/jackson/dataformat/cbor/CBORWriteContext.java) increments its index in `writeValue()`. This is supporting evidence for that context implementation, not a full CBOR generator test.

Conversely, the inspected [Avro ObjectWriteContext](https://github.com/FasterXML/jackson-dataformats-binary/blob/c9aedc5bcbcf8922854a60bb10a5e303c32477e0/avro/src/main/java/tools/jackson/dataformat/avro/ser/ObjectWriteContext.java) writes retained scalar values to its record and clears `_expectValue` without incrementing `_index`. [AvroGenerator](https://github.com/FasterXML/jackson-dataformats-binary/blob/c9aedc5bcbcf8922854a60bb10a5e303c32477e0/avro/src/main/java/tools/jackson/dataformat/avro/AvroGenerator.java) exposes its Avro context. The [ProtobufWriteContext](https://github.com/FasterXML/jackson-dataformats-binary/blob/c9aedc5bcbcf8922854a60bb10a5e303c32477e0/protobuf/src/main/java/tools/jackson/dataformat/protobuf/ProtobufWriteContext.java) similarly does not implement entry-index updates. These sources further undermine a universal count assumption; this audit did not construct full schema-backed #6299 reproductions for either format.

A custom generator can expose logical/snapshot context behavior different from JsonWriteContext, and a wrapper can intentionally discard writes. No new guarantee covering stable reference identity, emission counting, or suppression causality was found in the public API documentation inspected.

## Disposition and next design

**Reject the generic count-only helper based on the concrete FilteringGeneratorDelegate counterexample.** Preserve this delegate case as a meaningful retained-value negative control for any proposed replacement.

An explicit result from the existing inclusion/emission loop can distinguish databind suppression from generator behavior without inspecting output contexts or reevaluating inclusion. That is the next design under review. It must retain public/protected method compatibility and virtual dispatch as well as the already mapped lookup and exception semantics.

