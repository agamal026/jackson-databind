package tools.jackson.databind.ser.filter;

import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterizes the additional traversal introduced by the singleton guard.
 * Each acquired iterator is an independent, finite snapshot. No iterator is
 * modified while it is being traversed.
 */
class JsonIncludeUnwrappedContainer6299SnapshotTest
{
    private static final class SnapshotIterable implements Iterable<String> {
        private final boolean emptyFromThirdSnapshot;
        private int iteratorCalls;

        SnapshotIterable(boolean emptyFromThirdSnapshot) {
            this.emptyFromThirdSnapshot = emptyFromThirdSnapshot;
        }

        @Override
        public Iterator<String> iterator() {
            ++iteratorCalls;
            return emptyFromThirdSnapshot && iteratorCalls >= 3
                    ? List.<String>of().iterator() : List.of("keep").iterator();
        }
    }

    private ObjectWriter writer() {
        return JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .build()
                .writerFor(new TypeReference<Iterable<String>>() { });
    }

    @Test
    void stableRetainedSingletonIsUnchanged() {
        ObjectWriter writer = writer();
        assertEquals("\"keep\"", writer.without(
                SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS)
                .writeValueAsString(new SnapshotIterable(false)));
        assertEquals("\"keep\"", writer.writeValueAsString(new SnapshotIterable(false)));
    }

    @Test
    void retainedSingletonDoesNotRequireAnotherSnapshot() {
        ObjectWriter writer = writer();
        String control = writer.without(
                SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS)
                .writeValueAsString(new SnapshotIterable(true));
        assertEquals("\"keep\"", control);

        // The pre-existing singleton check and contents traversal both see
        // "keep". Only an additional traversal sees the subsequent empty view.
        String actual = writer.writeValueAsString(new SnapshotIterable(true));
        assertEquals(control, actual);
    }
}
