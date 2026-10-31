package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonIncludeUnwrappedContainer6299OverrideTest
{
    private static final class Probe {
        Iterable<?> expectedValue;
        JsonGenerator expectedGenerator;
        int resolvedCalls;
        int singletonCalls;
        int contentsCalls;
    }

    private static final class OverridingIterableSerializer
        extends IterableSerializer
    {
        private final Probe probe;

        OverridingIterableSerializer(JavaType elementType, Probe probe) {
            super(elementType, false, null);
            this.probe = probe;
        }

        private OverridingIterableSerializer(OverridingIterableSerializer src,
                TypeSerializer typeSerializer, ValueSerializer<?> elementSerializer,
                Boolean unwrapSingle, BeanProperty property,
                Object suppressableValue, boolean suppressNulls) {
            super(src, typeSerializer, elementSerializer, unwrapSingle,
                    property, suppressableValue, suppressNulls);
            probe = src.probe;
        }

        @Override
        public IterableSerializer withResolved(BeanProperty property,
                TypeSerializer typeSerializer, ValueSerializer<?> elementSerializer,
                Boolean unwrapSingle, Object suppressableValue, boolean suppressNulls) {
            ++probe.resolvedCalls;
            return new OverridingIterableSerializer(this, typeSerializer,
                    elementSerializer, unwrapSingle, property,
                    suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer typeSerializer) {
            return new OverridingIterableSerializer(this, typeSerializer,
                    _elementSerializer, _unwrapSingle, _property,
                    _suppressableValue, _suppressNulls);
        }

        @Override
        public boolean hasSingleElement(Iterable<?> value) {
            ++probe.singletonCalls;
            assertSame(probe.expectedValue, value);
            return super.hasSingleElement(value);
        }

        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.contentsCalls;
            assertSame(probe.expectedValue, value);
            assertSame(probe.expectedGenerator, g);
            g.writeString("override");
        }
    }

    @Test
    void retainedSingletonUsesContainerOverride() {
        assertContainerOverride("keep");
    }

    @Test
    void suppressedSingletonUsesContainerOverride() {
        assertContainerOverride("");
    }

    private void assertContainerOverride(String text) {
        Probe probe = new Probe();
        JavaType stringType = TypeFactory.createDefaultInstance().constructType(String.class);
        SimpleModule module = new SimpleModule();
        module.addSerializer(new OverridingIterableSerializer(stringType, probe));
        ObjectWriter writer = JsonMapper.builder()
                .addModule(module)
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .build()
                .writerFor(new TypeReference<Iterable<String>>() { });

        // Every iterator sees the same immutable physical singleton.
        Iterable<String> value = () -> List.of(text).iterator();
        probe.expectedValue = value;
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = writer.createGenerator(output)) {
            probe.expectedGenerator = generator;
            writer.writeValue(generator, value);
        }

        assertEquals("\"override\"", output.toString());
        assertTrue(probe.resolvedCalls > 0, "The subtype must survive contextual resolution");
        assertEquals(1, probe.singletonCalls);
        assertEquals(1, probe.contentsCalls);
    }
}
