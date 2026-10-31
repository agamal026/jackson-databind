package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.filter.FilteringGeneratorDelegate;
import tools.jackson.core.filter.TokenFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.jdk.IterableSerializer;
import tools.jackson.databind.type.TypeFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks an override which writes its value before delegating empty contents. */
class JsonIncludeUnwrappedIterable6299PrefixTest
{
    private static final class Probe {
        final boolean arrayPrefix;
        final boolean delegate;
        int calls;
        int completedDelegations;

        Probe(boolean arrayPrefix, boolean delegate) {
            this.arrayPrefix = arrayPrefix;
            this.delegate = delegate;
        }
    }

    private static final class PrefixSerializer extends IterableSerializer {
        private final Probe probe;

        PrefixSerializer(Probe probe) {
            super(TypeFactory.createDefaultInstance().constructType(String.class), false, null);
            this.probe = probe;
        }

        PrefixSerializer(PrefixSerializer src, TypeSerializer typeSerializer,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                BeanProperty property, Object suppressableValue, boolean suppressNulls) {
            super(src, typeSerializer, elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
            probe = src.probe;
        }

        @Override
        public IterableSerializer withResolved(BeanProperty property,
                TypeSerializer typeSerializer, ValueSerializer<?> elementSerializer,
                Boolean unwrapSingle, Object suppressableValue, boolean suppressNulls) {
            return new PrefixSerializer(this, typeSerializer, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer typeSerializer) {
            return new PrefixSerializer(this, typeSerializer, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }

        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.calls;
            if (probe.arrayPrefix) {
                g.writeStartArray();
            }
            g.writeString("prefix");
            if (probe.arrayPrefix) {
                g.writeEndArray();
            }
            if (probe.delegate) {
                // The sole element is always empty; ordinary content filtering
                // should not add a second value after the override's value.
                super.serializeContents(value, g, ctxt);
                ++probe.completedDelegations;
            }
        }
    }

    private String render(boolean arrayPrefix, boolean delegate,
            boolean objectProperty, boolean filtered) {
        Probe probe = new Probe(arrayPrefix, delegate);
        SimpleModule module = new SimpleModule();
        module.addSerializer(new PrefixSerializer(probe));
        ObjectWriter writer = JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .addModule(module).build()
                .writerFor(new TypeReference<Iterable<String>>() { });
        StringWriter output = new StringWriter();
        JsonGenerator original = writer.createGenerator(output);
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                return "after".equals(name) ? INCLUDE_ALL : this;
            }

            @Override
            public boolean includeString(String value) { return false; }

            @Override
            public boolean includeEmptyArray(boolean contentsFiltered) { return true; }
        };
        try (JsonGenerator g = filtered
                ? new FilteringGeneratorDelegate(original, filter,
                        TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
                : original) {
            if (objectProperty) {
                g.writeStartObject();
                g.writeName("values");
            }
            Iterable<String> value = () -> List.of("").iterator();
            writer.writeValue(g, value);
            if (objectProperty) {
                g.writeName("after");
                g.writeNumber(1);
                g.writeEndObject();
            }
        }
        assertEquals(1, probe.calls);
        assertEquals(delegate ? 1 : 0, probe.completedDelegations);
        return output.toString();
    }

    private void assertPrefix(boolean arrayPrefix, boolean property,
            boolean filtered, String expected) {
        // First prove the output with the same override and no super call.
        assertEquals(expected, render(arrayPrefix, false, property, filtered));
        String actual = assertDoesNotThrow(
                () -> render(arrayPrefix, true, property, filtered));
        assertEquals(expected, actual);
    }

    @Test
    void scalarBeforeSuperIsTheOnlyRootValue() {
        assertPrefix(false, false, false, "\"prefix\"");
    }

    @Test
    void completeContainerBeforeSuperIsTheOnlyRootValue() {
        assertPrefix(true, false, false, "[\"prefix\"]");
    }

    @Test
    void scalarBeforeSuperDoesNotBreakFollowingProperty() {
        assertPrefix(false, true, false, "{\"values\":\"prefix\",\"after\":1}");
    }

    @Test
    void filteredPrefixDoesNotReappearAsAnEmptyArray() {
        assertPrefix(false, true, true, "{\"after\":1}");
    }
}
