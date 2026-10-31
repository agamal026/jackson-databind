package tools.jackson.databind.ser.jdk;

import java.util.function.BooleanSupplier;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.SerializationContext;

/**
 * Shared handling for a physical singleton whose array wrapper would be omitted.
 */
final class UnwrappedContainerSerializerHelper
{
    private static final Object UNWRAPPED_CALL = new Object();

    private UnwrappedContainerSerializerHelper() { }

    /**
     * A suppressed singleton still needs to write a JSON value. Check lazily so
     * disabled content filtering does not inspect the element or its serializer.
     */
    static boolean writeEmptyArrayIfSuppressed(Object value, JsonGenerator g,
            boolean filtering, BooleanSupplier isEmpty)
    {
        if (filtering && isEmpty.getAsBoolean()) {
            g.writeStartArray(value, 0);
            g.writeEndArray();
            return true;
        }
        return false;
    }

    /**
     * Preserve virtual content dispatch while allowing the built-in traversal to
     * handle an empty unwrapped value. State belongs to this call, not the cached
     * serializer, and is restored even when a content override throws.
     */
    static void serializeUnwrapped(Object serializer, Object value, JsonGenerator g,
            SerializationContext ctxt, Runnable contents)
    {
        Object previous = ctxt.getAttribute(UNWRAPPED_CALL);
        ctxt.setAttribute(UNWRAPPED_CALL, new UnwrappedCall(serializer, value, g));
        try {
            contents.run();
        } finally {
            ctxt.setAttribute(UNWRAPPED_CALL, previous);
        }
    }

    /**
     * Only the first matching built-in content call may supply the empty array.
     * Direct calls, other values/generators and wrapped or typed re-entry must
     * not inherit an outer call's unwrapped shape.
     */
    static boolean claimUnwrapped(Object serializer, Object value, JsonGenerator g,
            SerializationContext ctxt)
    {
        Object attribute = ctxt.getAttribute(UNWRAPPED_CALL);
        if (attribute instanceof UnwrappedCall call
                && !call.claimed && call.serializer == serializer
                && call.value == value && call.generator == g
                && call.writeContext == g.streamWriteContext()) {
            call.claimed = true;
            return true;
        }
        return false;
    }

    private static final class UnwrappedCall {
        final Object serializer;
        final Object value;
        final JsonGenerator generator;
        final TokenStreamContext writeContext;
        boolean claimed;

        UnwrappedCall(Object serializer, Object value, JsonGenerator generator) {
            this.serializer = serializer;
            this.value = value;
            this.generator = generator;
            writeContext = generator.streamWriteContext();
        }
    }
}
