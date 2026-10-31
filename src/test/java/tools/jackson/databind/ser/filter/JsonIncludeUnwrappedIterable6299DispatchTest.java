package tools.jackson.databind.ser.filter;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonGenerator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JsonIncludeUnwrappedIterable6299DispatchTest
{
    private static final class Probe {
        final boolean suffix;
        Iterable<?> expectedValue;
        int singletonCalls;
        int contentsCalls;
        int completedDelegations;

        Probe(boolean suffix) { this.suffix = suffix; }
    }

    // Deliberately inherits serializeContents unchanged. An exact-runtime-class
    // guard would exclude this case even though dispatch reaches built-in code.
    private static class InheritedContentsSerializer extends IterableSerializer {
        final Probe probe;

        InheritedContentsSerializer(Probe probe) {
            super(TypeFactory.createDefaultInstance().constructType(String.class), false, null);
            this.probe = probe;
        }

        InheritedContentsSerializer(InheritedContentsSerializer src, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                BeanProperty property, Object suppressableValue, boolean suppressNulls) {
            super(src, vts, elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
            probe = src.probe;
        }

        @Override
        public IterableSerializer withResolved(BeanProperty property, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                Object suppressableValue, boolean suppressNulls) {
            return new InheritedContentsSerializer(this, vts, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer vts) {
            return new InheritedContentsSerializer(this, vts, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }

        @Override
        public boolean hasSingleElement(Iterable<?> value) {
            ++probe.singletonCalls;
            assertSame(probe.expectedValue, value);
            return super.hasSingleElement(value);
        }
    }

    private static class CustomContentsSerializer extends InheritedContentsSerializer {
        CustomContentsSerializer(Probe probe) { super(probe); }

        CustomContentsSerializer(CustomContentsSerializer src, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                BeanProperty property, Object suppressableValue, boolean suppressNulls) {
            super(src, vts, elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
        }

        @Override
        public IterableSerializer withResolved(BeanProperty property, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                Object suppressableValue, boolean suppressNulls) {
            return new CustomContentsSerializer(this, vts, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer vts) {
            return new CustomContentsSerializer(this, vts, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }

        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.contentsCalls;
            assertSame(probe.expectedValue, value);
            super.serializeContents(value, g, ctxt);
            ++probe.completedDelegations;
            super.serializeContents(value, g, ctxt);
            ++probe.completedDelegations;
            if (probe.suffix) {
                g.writeString("suffix");
            }
        }
    }

    // Inherits a custom override from an intermediate class. Looking only at
    // getDeclaredMethod() on this leaf would miss that override.
    private static final class InheritedCustomSerializer extends CustomContentsSerializer {
        InheritedCustomSerializer(Probe probe) { super(probe); }

        InheritedCustomSerializer(InheritedCustomSerializer src, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                BeanProperty property, Object suppressableValue, boolean suppressNulls) {
            super(src, vts, elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
        }

        @Override
        public IterableSerializer withResolved(BeanProperty property, TypeSerializer vts,
                ValueSerializer<?> elementSerializer, Boolean unwrapSingle,
                Object suppressableValue, boolean suppressNulls) {
            return new InheritedCustomSerializer(this, vts, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer vts) {
            return new InheritedCustomSerializer(this, vts, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }
    }

    private String render(InheritedContentsSerializer serializer, String text,
            boolean filtering) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(serializer);
        ObjectWriter writer = JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .addModule(module).build()
                .writerFor(new TypeReference<Iterable<String>>() { });
        if (!filtering) {
            writer = writer.without(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS);
        }
        Iterable<String> value = () -> List.of(text).iterator();
        serializer.probe.expectedValue = value;
        String result = writer.writeValueAsString(value);
        assertEquals(1, serializer.probe.singletonCalls);
        return result;
    }

    @Test
    void customOnlyDelegatesRetainsLegacyEmptyOutput() {
        Probe probe = new Probe(false);
        // This characterizes the custom hook's historical empty output, not
        // a valid JSON document or the desired built-in singleton behavior.
        assertEquals("", render(new CustomContentsSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    @Test
    void customSuffixIsNotPrefixedWithAnEmptyArray() {
        Probe probe = new Probe(true);
        assertEquals("\"suffix\"", render(new CustomContentsSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    @Test
    void inheritedCustomOverrideRetainsOwnership() {
        Probe probe = new Probe(true);
        assertEquals("\"suffix\"", render(new InheritedCustomSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    @Test
    void inheritedDefaultContentsProducesEmptyArray() {
        assertEquals("[]", render(new InheritedContentsSerializer(new Probe(false)), "", true));
    }

    @Test
    void inheritedDefaultContentsKeepsScalar() {
        assertEquals("\"keep\"", render(new InheritedContentsSerializer(new Probe(false)), "keep", true));
    }

    @Test
    void inheritedDefaultContentsWithFilteringDisabledKeepsEmptyString() {
        assertEquals("\"\"", render(new InheritedContentsSerializer(new Probe(false)), "", false));
    }
}
