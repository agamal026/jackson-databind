package validation;

import java.util.List;

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


public class NativeDispatchProbe
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

    void customOnlyDelegatesRetainsLegacyEmptyOutput() {
        Probe probe = new Probe(false);
        // This characterizes the custom hook's historical empty output, not
        // a valid JSON document or the desired built-in singleton behavior.
        assertEquals("", render(new CustomContentsSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    void customSuffixIsNotPrefixedWithAnEmptyArray() {
        Probe probe = new Probe(true);
        assertEquals("\"suffix\"", render(new CustomContentsSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    void inheritedCustomOverrideRetainsOwnership() {
        Probe probe = new Probe(true);
        assertEquals("\"suffix\"", render(new InheritedCustomSerializer(probe), "", true));
        assertEquals(1, probe.contentsCalls);
        assertEquals(2, probe.completedDelegations);
    }

    void inheritedDefaultContentsProducesEmptyArray() {
        assertEquals(corrected ? "[]" : "", render(new InheritedContentsSerializer(new Probe(false)), "", true));
    }

    void inheritedDefaultContentsKeepsScalar() {
        assertEquals("\"keep\"", render(new InheritedContentsSerializer(new Probe(false)), "keep", true));
    }

    void inheritedDefaultContentsWithFilteringDisabledKeepsEmptyString() {
        assertEquals("\"\"", render(new InheritedContentsSerializer(new Probe(false)), "", false));
    }

    private static boolean corrected;

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("original iterable identity changed");
        }
    }

    void builtInDefaultContentsProducesEmptyArray() {
        ObjectWriter writer = JsonMapper.builder()
                .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                        JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_EMPTY))
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .build().writerFor(new TypeReference<Iterable<String>>() { });
        Iterable<String> value = () -> List.of("").iterator();
        assertEquals(corrected ? "[]" : "", writer.writeValueAsString(value));
    }

    private void runCase(String name) {
        System.out.println("CASE " + name);
        switch (name) {
        case "custom-empty": customOnlyDelegatesRetainsLegacyEmptyOutput(); break;
        case "custom-suffix": customSuffixIsNotPrefixedWithAnEmptyArray(); break;
        case "inherited-custom": inheritedCustomOverrideRetainsOwnership(); break;
        case "inherited-empty": inheritedDefaultContentsProducesEmptyArray(); break;
        case "inherited-kept": inheritedDefaultContentsKeepsScalar(); break;
        case "filtering-disabled": inheritedDefaultContentsWithFilteringDisabledKeepsEmptyString(); break;
        case "builtin-empty": builtInDefaultContentsProducesEmptyArray(); break;
        default: throw new IllegalArgumentException("Unknown case: " + name);
        }
        System.out.println("PASS " + (corrected ? "candidate" : "baseline") + " " + name);
    }

    public static void main(String[] args) {
        if ((args.length != 1 && args.length != 2)
                || !(args[0].equals("baseline") || args[0].equals("candidate"))) {
            throw new IllegalArgumentException("Expected baseline or candidate, optionally a case name");
        }
        corrected = args[0].equals("candidate");
        NativeDispatchProbe test = new NativeDispatchProbe();
        if (args.length == 2) {
            test.runCase(args[1]);
            return;
        }
        for (String name : new String[] { "custom-empty", "custom-suffix", "inherited-custom",
                "inherited-empty", "inherited-kept", "filtering-disabled", "builtin-empty" }) {
            test.runCase(name);
        }
        System.out.println("PASS " + args[0] + " 7/7 dispatch cases");
    }
}
