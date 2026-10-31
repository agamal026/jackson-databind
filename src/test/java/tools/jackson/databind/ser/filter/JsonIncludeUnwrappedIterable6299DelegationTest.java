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

/**
 * Characterizes the prepatch behavior of custom content overrides. Empty output
 * here is a legacy limitation, not a claim that it is valid standalone JSON.
 */
class JsonIncludeUnwrappedIterable6299DelegationTest
{
    private static final class Probe {
        final int delegations;
        int overrideCalls;
        int completedDelegations;

        Probe(int delegations) { this.delegations = delegations; }
    }

    private static final class DelegatingSerializer extends IterableSerializer {
        private final Probe probe;

        DelegatingSerializer(Probe probe) {
            super(TypeFactory.createDefaultInstance().constructType(String.class), false, null);
            this.probe = probe;
        }

        DelegatingSerializer(DelegatingSerializer src, TypeSerializer typeSerializer,
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
            return new DelegatingSerializer(this, typeSerializer, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer typeSerializer) {
            return new DelegatingSerializer(this, typeSerializer, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }

        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.overrideCalls;
            for (int i = 0; i < probe.delegations; ++i) {
                super.serializeContents(value, g, ctxt);
                ++probe.completedDelegations;
            }
        }
    }

    private String render(String text, int delegations) {
        Probe probe = new Probe(delegations);
        SimpleModule module = new SimpleModule();
        module.addSerializer(new DelegatingSerializer(probe));
        ObjectWriter writer = JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .addModule(module).build()
                .writerFor(new TypeReference<Iterable<String>>() { });
        Iterable<String> value = () -> List.of(text).iterator();
        String result = writer.writeValueAsString(value);
        assertEquals(1, probe.overrideCalls);
        assertEquals(delegations, probe.completedDelegations);
        return result;
    }

    @Test
    void suppressedContentsDelegatedOnceRetainLegacyOutput() {
        assertEquals("", render("", 1));
    }

    @Test
    void suppressedContentsDelegatedTwiceRetainLegacyOutput() {
        assertEquals("", render("", 2));
    }

    @Test
    void retainedContentsDelegatedOnceStillWriteTheValue() {
        assertEquals("\"keep\"", render("keep", 1));
    }
}
