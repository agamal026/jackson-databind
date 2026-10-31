package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.EnumSet;

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
import tools.jackson.databind.ser.jdk.EnumSetSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.TypeFactory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonIncludeUnwrappedEnumSet6299OverrideTest
{
    private enum Element { EMPTY, KEEP }

    private static final class ElementSerializer extends StdSerializer<Element> {
        ElementSerializer() { super(Element.class); }

        @Override
        public boolean isEmpty(SerializationContext ctxt, Element value) {
            return value == Element.EMPTY;
        }

        @Override
        public void serialize(Element value, JsonGenerator g, SerializationContext ctxt) {
            g.writeString(value.name());
        }
    }

    private static final class Probe {
        final boolean delegate;
        int calls;
        int completedDelegations;

        Probe(boolean delegate) { this.delegate = delegate; }
    }

    private static final class OverrideSerializer extends EnumSetSerializer {
        private final Probe probe;

        OverrideSerializer(Probe probe) {
            super(TypeFactory.createDefaultInstance().constructType(Element.class));
            this.probe = probe;
        }

        OverrideSerializer(OverrideSerializer src, TypeSerializer typeSerializer,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                BeanProperty property, Object suppressableValue, boolean suppressNulls) {
            super(src, typeSerializer, elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
            probe = src.probe;
        }

        @Override
        public EnumSetSerializer withResolved(BeanProperty property,
                TypeSerializer typeSerializer, ValueSerializer<?> elementSerializer,
                Boolean unwrapSingle, Object suppressableValue, boolean suppressNulls) {
            return new OverrideSerializer(this, typeSerializer, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        public void serializeContents(EnumSet<? extends Enum<?>> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.calls;
            g.writeString("override");
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
                ++probe.completedDelegations;
            }
        }
    }

    private void assertOverride(Element element, boolean delegate, boolean filtered,
            String expected) {
        Probe probe = new Probe(delegate);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Element.class, new ElementSerializer());
        module.addSerializer(new OverrideSerializer(probe));
        ObjectWriter writer = JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .addModule(module).build()
                .writerFor(new TypeReference<EnumSet<Element>>() { });
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
            if (filtered) {
                g.writeStartObject();
                g.writeName("values");
            }
            writer.writeValue(g, EnumSet.of(element));
            if (filtered) {
                g.writeName("after");
                g.writeNumber(1);
                g.writeEndObject();
            }
        }
        assertAll(
                () -> assertEquals(expected, output.toString()),
                () -> assertEquals(1, probe.calls),
                () -> assertEquals(delegate ? 1 : 0, probe.completedDelegations));
    }

    @Test
    void suppressedSingletonStillDispatchesTheOverride() {
        assertOverride(Element.EMPTY, false, false, "\"override\"");
    }

    @Test
    void prefixBeforeSuppressedSuperCallRemainsTheOnlyValue() {
        assertOverride(Element.EMPTY, true, false, "\"override\"");
    }

    @Test
    void retainedSingletonStillDispatchesTheOverride() {
        assertOverride(Element.KEEP, false, false, "\"override\"");
    }

    @Test
    void filteredOverrideIsNotReplacedWithAnEmptyArray() {
        assertOverride(Element.EMPTY, true, true, "{\"after\":1}");
    }
}
