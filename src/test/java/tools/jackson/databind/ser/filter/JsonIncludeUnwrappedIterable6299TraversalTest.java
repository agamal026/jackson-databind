package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.filter.FilteringGeneratorDelegate;
import tools.jackson.core.filter.TokenFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.util.JsonGeneratorDelegate;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.jsontype.impl.AsArrayTypeSerializer;
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.jdk.IterableSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.TypeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonIncludeUnwrappedIterable6299TraversalTest
{
    private static final TypeReference<Iterable<String>> STRINGS = new TypeReference<>() { };

    private static final class Item {
        final boolean empty;
        int checks;
        int writes;

        Item(boolean empty) {
            this.empty = empty;
        }
    }

    private static final class ItemSerializer extends StdSerializer<Item> {
        ItemSerializer() { super(Item.class); }

        @Override
        public boolean isEmpty(SerializationContext ctxt, Item value) {
            return ++value.checks > 1 || value.empty;
        }

        @Override
        public void serialize(Item value, JsonGenerator g, SerializationContext ctxt) {
            ++value.writes;
            g.writeString("keep");
        }
    }

    private JsonMapper.Builder builder() {
        return JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
    }

    private ObjectWriter itemWriter() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Item.class, new ItemSerializer());
        return builder().addModule(module).build()
                .writerFor(new TypeReference<Iterable<Item>>() { });
    }

    @Test
    void keptElementIsCheckedOnce() {
        Item item = new Item(false);
        Iterable<Item> value = () -> List.of(item).iterator();
        assertEquals("\"keep\"", itemWriter().writeValueAsString(value));
        assertEquals(1, item.checks);
        assertEquals(1, item.writes);
    }

    @Test
    void suppressedElementIsCheckedOnce() {
        Item item = new Item(true);
        Iterable<Item> value = () -> List.of(item).iterator();
        assertEquals("[]", itemWriter().writeValueAsString(value));
        assertEquals(1, item.checks);
        assertEquals(0, item.writes);
    }

    @Test
    void disabledFilteringDoesNotCheckEmptiness() {
        Item item = new Item(true);
        Iterable<Item> value = () -> List.of(item).iterator();
        assertEquals("\"keep\"", itemWriter()
                .without(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS)
                .writeValueAsString(value));
        assertEquals(0, item.checks);
        assertEquals(1, item.writes);
    }

    @Test
    void wrappedAndMultipleValuesDoNotAcquireAnExtraArray() {
        ObjectWriter writer = builder().build().writerFor(STRINGS);
        Iterable<String> singleton = () -> List.of("").iterator();
        Iterable<String> multiple = () -> List.of("", "").iterator();
        assertEquals("[]", writer.without(
                SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .writeValueAsString(singleton));
        assertEquals("[]", writer.writeValueAsString(multiple));
    }

    @Test
    void nullSingletonRetainsTheConfiguredInclusion() {
        ObjectWriter writer = builder().build().writerFor(STRINGS);
        Iterable<String> value = () -> Collections.<String>singletonList(null).iterator();
        assertEquals("[]", writer.writeValueAsString(value));
        assertEquals("null", writer.without(
                SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS)
                .writeValueAsString(value));
    }

    @Test
    void actualGeneratorFilteringDoesNotSynthesizeARetainedValue() {
        assertEquals("{\"after\":1}", filtered("keep"));
        assertEquals("{\"values\":[],\"after\":1}", filtered(""));
    }

    private String filtered(String text) {
        ObjectWriter writer = builder().build().writerFor(STRINGS);
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
        StringWriter output = new StringWriter();
        try (JsonGenerator g = new FilteringGeneratorDelegate(writer.createGenerator(output),
                filter, TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)) {
            g.writeStartObject();
            g.writeName("values");
            Iterable<String> value = () -> List.of(text).iterator();
            writer.writeValue(g, value);
            g.writeName("after");
            g.writeNumber(1);
            g.writeEndObject();
        }
        return output.toString();
    }

    private enum Mode { WRAPPED, TYPED, OTHER_VALUE, OTHER_GENERATOR, OTHER_SERIALIZER, TWICE }

    private static final class IterableTypeIdResolver extends TypeIdResolverBase {
        @Override
        public String idFromValue(DatabindContext ctxt, Object value) { return "iterable"; }

        @Override
        public String idFromValueAndType(DatabindContext ctxt, Object value, Class<?> type) {
            return "iterable";
        }

        @Override
        public JavaType typeFromId(DatabindContext ctxt, String id) {
            return ctxt.constructType(Iterable.class);
        }

        @Override
        public JsonTypeInfo.Id getMechanism() { return JsonTypeInfo.Id.CUSTOM; }
    }

    private static final class Probe {
        final Mode mode;
        int calls;

        Probe(Mode mode) { this.mode = mode; }
    }

    private static final class ReentrantSerializer extends IterableSerializer {
        final Probe probe;

        ReentrantSerializer(Probe probe) {
            super(TypeFactory.createDefaultInstance().constructType(String.class), false, null);
            this.probe = probe;
        }

        ReentrantSerializer(ReentrantSerializer src, TypeSerializer typeSerializer,
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
            return new ReentrantSerializer(this, typeSerializer, elementSerializer,
                    unwrapSingle, property, suppressableValue, suppressNulls);
        }

        @Override
        protected IterableSerializer _withValueTypeSerializer(TypeSerializer typeSerializer) {
            return new ReentrantSerializer(this, typeSerializer, _elementSerializer,
                    _unwrapSingle, _property, _suppressableValue, _suppressNulls);
        }

        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator g,
                SerializationContext ctxt) {
            ++probe.calls;
            if (probe.mode == Mode.TYPED && probe.calls > 1) {
                super.serializeContents(value, g, ctxt);
                return;
            }
            switch (probe.mode) {
            case WRAPPED:
                g.writeStartArray(value);
                super.serializeContents(value, g, ctxt);
                g.writeEndArray();
                break;
            case TYPED:
                super.serializeWithType(value, g, ctxt,
                        new AsArrayTypeSerializer(new IterableTypeIdResolver(), null));
                break;
            case OTHER_VALUE:
                Iterable<String> other = () -> List.of("").iterator();
                super.serializeContents(other, g, ctxt);
                g.writeString("override");
                break;
            case OTHER_GENERATOR:
                super.serializeContents(value, new JsonGeneratorDelegate(g), ctxt);
                g.writeString("override");
                break;
            case OTHER_SERIALIZER:
                IterableSerializer otherSerializer = new IterableSerializer(this,
                        _valueTypeSerializer, _elementSerializer, _unwrapSingle,
                        _property, _suppressableValue, _suppressNulls);
                otherSerializer.serializeContents(value, g, ctxt);
                g.writeString("override");
                break;
            case TWICE:
                super.serializeContents(value, g, ctxt);
                super.serializeContents(value, g, ctxt);
                break;
            }
        }
    }

    @Test
    void wrappedReentryDoesNotInheritUnwrapping() { assertReentry(Mode.WRAPPED, "[]"); }

    @Test
    void typedReentryDoesNotInheritUnwrapping() {
        assertReentry(Mode.TYPED, "[\"iterable\",[]]");
    }

    @Test
    void anotherValueDoesNotInheritUnwrapping() { assertReentry(Mode.OTHER_VALUE, "\"override\""); }

    @Test
    void anotherGeneratorDoesNotInheritUnwrapping() {
        assertReentry(Mode.OTHER_GENERATOR, "\"override\"");
    }

    @Test
    void anotherSerializerDoesNotInheritUnwrapping() {
        assertReentry(Mode.OTHER_SERIALIZER, "\"override\"");
    }

    @Test
    void repeatedCustomContentsCallsRetainLegacyEmptyOutput() {
        // This override, not the built-in serializer, owns the value's shape.
        // Two direct contents calls historically emit no suppressed elements.
        assertReentry(Mode.TWICE, "");
    }

    private void assertReentry(Mode mode, String expected) {
        Probe probe = new Probe(mode);
        SimpleModule module = new SimpleModule();
        module.addSerializer(new ReentrantSerializer(probe));
        ObjectWriter writer = builder().addModule(module).build().writerFor(STRINGS);
        Iterable<String> value = () -> List.of("").iterator();
        assertEquals(expected, writer.writeValueAsString(value));
        assertEquals(mode == Mode.TYPED ? 2 : 1, probe.calls);
    }
}
