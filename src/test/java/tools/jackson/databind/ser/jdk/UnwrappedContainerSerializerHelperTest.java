package tools.jackson.databind.ser.jdk;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnwrappedContainerSerializerHelperTest
{
    private static final class Probe {
        final BiConsumer<JsonGenerator, SerializationContext> action;

        Probe(BiConsumer<JsonGenerator, SerializationContext> action) {
            this.action = action;
        }
    }

    private static final class ProbeSerializer extends StdSerializer<Probe> {
        ProbeSerializer() { super(Probe.class); }

        @Override
        public void serialize(Probe value, JsonGenerator g, SerializationContext ctxt) {
            value.action.accept(g, ctxt);
            g.writeString("ok");
        }
    }

    private void inContext(BiConsumer<JsonGenerator, SerializationContext> action) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Probe.class, new ProbeSerializer());
        assertEquals("\"ok\"", JsonMapper.builder().addModule(module).build()
                .writeValueAsString(new Probe(action)));
    }

    @Test
    void aDirectCallHasNoUnwrappedScope() {
        inContext((g, ctxt) -> assertFalse(
                UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt)));
    }

    @Test
    void scopeCanBeClaimedOnlyOnce() {
        inContext((g, ctxt) -> {
            UnwrappedContainerSerializerHelper.serializeUnwrapped(this, this, g, ctxt, () -> {
                assertTrue(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
                assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
            });
            assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
        });
    }

    @Test
    void nestedScopeRestoresItsParent() {
        inContext((g, ctxt) -> {
            Object nested = new Object();
            UnwrappedContainerSerializerHelper.serializeUnwrapped(this, this, g, ctxt, () -> {
                UnwrappedContainerSerializerHelper.serializeUnwrapped(nested, nested, g, ctxt, () -> {
                    assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
                    assertTrue(UnwrappedContainerSerializerHelper.claimUnwrapped(nested, nested, g, ctxt));
                });
                assertTrue(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
            });
            assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
        });
    }

    @Test
    void throwingNestedScopeRestoresItsParent() {
        inContext((g, ctxt) -> {
            Object nested = new Object();
            UnwrappedContainerSerializerHelper.serializeUnwrapped(this, this, g, ctxt, () -> {
                assertThrows(IllegalStateException.class, () ->
                        UnwrappedContainerSerializerHelper.serializeUnwrapped(nested, nested, g, ctxt, () -> {
                            throw new IllegalStateException("probe");
                        }));
                assertTrue(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
            });
            assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(nested, nested, g, ctxt));
        });
    }

    @Test
    void throwingOuterScopeDoesNotLeak() {
        inContext((g, ctxt) -> {
            assertThrows(IllegalStateException.class, () ->
                    UnwrappedContainerSerializerHelper.serializeUnwrapped(this, this, g, ctxt, () -> {
                        throw new IllegalStateException("probe");
                    }));
            assertFalse(UnwrappedContainerSerializerHelper.claimUnwrapped(this, this, g, ctxt));
        });
    }
}
