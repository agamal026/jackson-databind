package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.impl.AsArrayTypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.jdk.EnumSetSerializer;
import tools.jackson.databind.ser.jdk.JDKArraySerializers.*;
import tools.jackson.databind.ser.jdk.ObjectArraySerializer;
import tools.jackson.databind.ser.jdk.StringArraySerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.TypeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Public contents hooks remain responsible for the shape of custom output. */
class JsonIncludeUnwrappedContainer6299SubclassTest
{
    enum Choice { EMPTY }

    private static final class Probe {
        final boolean delegate;
        Object expectedValue;
        JsonGenerator expectedGenerator;
        int calls;

        Probe(boolean delegate) { this.delegate = delegate; }

        void write(Object value, JsonGenerator g) {
            assertSame(expectedValue, value);
            assertSame(expectedGenerator, g);
            ++calls;
            g.writeString("override");
        }
    }

    private static final class Invocation {
        final Object value;
        final ValueSerializer<?> serializer;
        final Probe probe;

        Invocation(Object value, ValueSerializer<?> serializer, Probe probe) {
            this.value = value;
            this.serializer = serializer;
            this.probe = probe;
        }
    }

    private static final class InvocationSerializer extends StdSerializer<Invocation> {
        InvocationSerializer() { super(Invocation.class); }

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(Invocation invocation, JsonGenerator g, SerializationContext ctxt) {
            invocation.probe.expectedValue = invocation.value;
            invocation.probe.expectedGenerator = g;
            ((ValueSerializer<Object>) invocation.serializer).serialize(invocation.value, g, ctxt);
        }
    }

    private static final class EnumOverride extends EnumSetSerializer {
        private final Probe probe;

        EnumOverride(Probe probe) {
            super(new EnumSetSerializer(TypeFactory.createDefaultInstance().constructType(Choice.class)),
                    null, null, true, null, Choice.EMPTY, true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(EnumSet<? extends Enum<?>> value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class ObjectOverride extends ObjectArraySerializer {
        private final Probe probe;

        ObjectOverride(Probe probe) {
            super(new ObjectArraySerializer(TypeFactory.createDefaultInstance().constructType(String.class),
                    false, null, null), null, null, null, true, "", true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(Object[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class StringOverride extends StringArraySerializer {
        private final Probe probe;

        StringOverride(Probe probe) {
            super(StringArraySerializer.instance, null, null, true, "", true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(String[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class BooleanOverride extends BooleanArraySerializer {
        private final Probe probe;

        BooleanOverride(Probe probe) {
            super(new BooleanArraySerializer(), null, true, false, true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(boolean[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class ShortOverride extends ShortArraySerializer {
        private final Probe probe;

        ShortOverride(Probe probe) {
            super(new ShortArraySerializer(), null, true, Short.valueOf((short) 0), true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(short[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class IntOverride extends IntArraySerializer {
        private final Probe probe;

        IntOverride(Probe probe) {
            super(new IntArraySerializer(), null, true, Integer.valueOf(0), true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(int[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class LongOverride extends LongArraySerializer {
        private final Probe probe;

        LongOverride(Probe probe) {
            super(new LongArraySerializer(), null, true, Long.valueOf(0L), true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(long[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class FloatOverride extends FloatArraySerializer {
        private final Probe probe;

        FloatOverride(Probe probe) {
            super(new FloatArraySerializer(), null, true, Float.valueOf(0.0f), true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(float[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class DoubleOverride extends DoubleArraySerializer {
        private final Probe probe;

        DoubleOverride(Probe probe) {
            super(new DoubleArraySerializer(), null, true, Double.valueOf(0.0), true);
            this.probe = probe;
        }

        @Override
        public void serializeContents(double[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContents(value, g, ctxt);
            }
        }
    }

    private static final class UsingOverride extends ObjectArraySerializer {
        private final Probe probe;

        UsingOverride(Probe probe) {
            super(new ObjectArraySerializer(TypeFactory.createDefaultInstance().constructType(String.class),
                    false, null, null), null, null, new UnusedElementSerializer(), true, "", true);
            this.probe = probe;
        }

        @Override
        public void serializeContentsUsing(Object[] value, JsonGenerator g,
                SerializationContext ctxt, ValueSerializer<Object> serializer) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeContentsUsing(value, g, ctxt, serializer);
            }
        }
    }

    private static final class TypedOverride extends ObjectArraySerializer {
        private final Probe probe;

        TypedOverride(Probe probe) {
            // The type serializer selects the public typed-contents hook. The
            // empty element is suppressed, so no type id is requested from it.
            super(new ObjectArraySerializer(TypeFactory.createDefaultInstance().constructType(String.class),
                    false, null, null), null, new AsArrayTypeSerializer(null, null),
                    null, true, "", true);
            this.probe = probe;
        }

        @Override
        public void serializeTypedContents(Object[] value, JsonGenerator g, SerializationContext ctxt) {
            probe.write(value, g);
            if (probe.delegate) {
                super.serializeTypedContents(value, g, ctxt);
            }
        }
    }

    private static final class UnusedElementSerializer extends StdSerializer<Object> {
        UnusedElementSerializer() { super(Object.class); }

        @Override
        public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
            throw new AssertionError("Suppressed content must not reach its element serializer");
        }
    }

    static Stream<Arguments> contentsHooks() {
        return Stream.of(
            Arguments.of("enum contents", EnumSet.of(Choice.EMPTY),
                    (Function<Probe, ValueSerializer<?>>) EnumOverride::new),
            Arguments.of("object contents", new Object[]{""},
                    (Function<Probe, ValueSerializer<?>>) ObjectOverride::new),
            Arguments.of("string contents", new String[]{""},
                    (Function<Probe, ValueSerializer<?>>) StringOverride::new),
            Arguments.of("boolean contents", new boolean[]{false},
                    (Function<Probe, ValueSerializer<?>>) BooleanOverride::new),
            Arguments.of("short contents", new short[]{0},
                    (Function<Probe, ValueSerializer<?>>) ShortOverride::new),
            Arguments.of("int contents", new int[]{0},
                    (Function<Probe, ValueSerializer<?>>) IntOverride::new),
            Arguments.of("long contents", new long[]{0},
                    (Function<Probe, ValueSerializer<?>>) LongOverride::new),
            Arguments.of("float contents", new float[]{0},
                    (Function<Probe, ValueSerializer<?>>) FloatOverride::new),
            Arguments.of("double contents", new double[]{0},
                    (Function<Probe, ValueSerializer<?>>) DoubleOverride::new),
            Arguments.of("object using hook", new Object[]{""},
                    (Function<Probe, ValueSerializer<?>>) UsingOverride::new),
            Arguments.of("object typed hook", new Object[]{""},
                    (Function<Probe, ValueSerializer<?>>) TypedOverride::new)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentsHooks")
    void customOutputReplacesSuppressedNativeContents(String name, Object value,
            Function<Probe, ValueSerializer<?>> factory) {
        assertOverride(value, factory, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentsHooks")
    void delegatingAfterCustomOutputDoesNotAddAnEmptyArray(String name, Object value,
            Function<Probe, ValueSerializer<?>> factory) {
        assertOverride(value, factory, true);
    }

    private void assertOverride(Object value, Function<Probe, ValueSerializer<?>> factory,
            boolean delegate) {
        Probe probe = new Probe(delegate);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Invocation.class, new InvocationSerializer());
        JsonMapper mapper = JsonMapper.builder().addModule(module)
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .build();
        StringWriter output = new StringWriter();
        try (JsonGenerator g = mapper.createGenerator(output)) {
            g.writeStartObject();
            g.writeName("values");
            mapper.writeValue(g, new Invocation(value, factory.apply(probe), probe));
            g.writeName("after");
            g.writeNumber(1);
            g.writeEndObject();
        }
        assertEquals("{\"values\":\"override\",\"after\":1}", output.toString());
        assertEquals(1, probe.calls);
    }
}
