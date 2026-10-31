package validation;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Experimental build-time integration for the frozen #6299 dispatch candidate.
 * Registers type-query metadata only, never reflective method invocation.
 * No application serializer class names or classpath-wide scan are required.
 */
public final class IterableContentsFeature implements Feature
{
    private final Set<String> registeredTypes = new ConcurrentSkipListSet<>();

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Class<?> base = access.findClassByName(
                "tools.jackson.databind.ser.jdk.IterableSerializer");
        if (base == null) {
            return;
        }
        access.registerSubtypeReachabilityHandler((during, type) -> {
            RuntimeReflection.register(type);
            registeredTypes.add(type.getName());
        }, base);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        // Deterministic evidence; callbacks may execute concurrently.
        for (String name : registeredTypes) {
            System.out.println("AUTO-QUERY " + name);
        }
    }
}
