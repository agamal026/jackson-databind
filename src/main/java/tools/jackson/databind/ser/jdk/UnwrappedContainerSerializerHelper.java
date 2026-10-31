package tools.jackson.databind.ser.jdk;

import tools.jackson.core.JsonGenerator;

/**
 * Shared handling for a physical singleton whose array wrapper would be omitted.
 */
final class UnwrappedContainerSerializerHelper
{
    private UnwrappedContainerSerializerHelper() { }

    /**
     * Called after native contents traversal. The flag records whether content
     * inclusion suppressed every element, independently of generator filtering.
     */
    static void writeEmptyArrayIfNeeded(Object value, JsonGenerator g,
            boolean emptyUnwrapped)
    {
        if (emptyUnwrapped) {
            g.writeStartArray(value, 0);
            g.writeEndArray();
        }
    }
}
