package tools.jackson.databind.ser.filter;

import java.io.StringWriter;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.filter.FilteringGeneratorDelegate;
import tools.jackson.core.filter.TokenFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.HandlerInstantiator;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.TypeResolverBuilder;
import tools.jackson.databind.ser.filter.JsonIncludeUnwrappedContainer6299Test.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonIncludeUnwrappedContainer6299SinglePassTest
{
    private static final class FirstInclusionFilter {
        int calls;

        @Override
        public boolean equals(Object value) {
            // Contextualization may ask whether null is excluded. Only content
            // decisions advance this probe; a second check rejects the value.
            return (value != null) && (++calls > 1);
        }

        @Override
        public int hashCode() { return 0; }
    }

    private static final class FilterInstantiator extends HandlerInstantiator {
        final FirstInclusionFilter filter = new FirstInclusionFilter();

        @Override
        public Object includeFilterInstance(SerializationConfig config,
                BeanPropertyDefinition property, Class<?> filterClass) {
            return filterClass == FirstInclusionFilter.class ? filter : null;
        }

        @Override
        public ValueDeserializer<?> deserializerInstance(DeserializationConfig config,
                Annotated annotated, Class<?> type) { return null; }

        @Override
        public KeyDeserializer keyDeserializerInstance(DeserializationConfig config,
                Annotated annotated, Class<?> type) { return null; }

        @Override
        public ValueSerializer<?> serializerInstance(SerializationConfig config,
                Annotated annotated, Class<?> type) { return null; }

        @Override
        public TypeResolverBuilder<?> typeResolverBuilderInstance(MapperConfig<?> config,
                Annotated annotated, Class<?> type) { return null; }

        @Override
        public TypeIdResolver typeIdResolverInstance(MapperConfig<?> config,
                Annotated annotated, Class<?> type) { return null; }
    }

    static Stream<Arguments> retainedSingletons() {
        return JsonIncludeUnwrappedContainer6299Test.containers().map(row -> {
            Object[] values = row.get();
            return Arguments.of(values[0], values[1], values[3], values[6]);
        });
    }

    static Stream<Arguments> filteringSingletons() {
        return JsonIncludeUnwrappedContainer6299Test.containers().map(row -> {
            Object[] values = row.get();
            return Arguments.of(values[0], values[1], values[2], values[3], values[5]);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retainedSingletons")
    void retainedContentUsesItsFirstInclusionDecision(String name, TypeReference<?> type,
            Object retained, String expected) {
        FilterInstantiator instantiator = new FilterInstantiator();
        JsonInclude.Value inclusion = JsonInclude.Value.construct(
                JsonInclude.Include.ALWAYS, JsonInclude.Include.CUSTOM)
                .withContentFilter(FirstInclusionFilter.class);
        JsonMapper mapper = builder(inclusion).handlerInstantiator(instantiator).build();
        assertEquals("{\"values\":" + expected + ",\"after\":1}",
                mapper.writerFor(type).writeValueAsString(new Bean<>(retained)));
        assertEquals(1, instantiator.filter.calls);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filteringSingletons")
    void generatorFilteringDoesNotChangeContentInclusion(String name, TypeReference<?> type,
            Object suppressed, Object retained, JsonInclude.Value inclusion) {
        ObjectWriter writer = builder(inclusion).build().writerFor(type);
        assertEquals("{\"after\":1}", filtered(writer, retained));
        assertEquals("{\"values\":[],\"after\":1}", filtered(writer, suppressed));
    }

    private JsonMapper.Builder builder(JsonInclude.Value inclusion) {
        return JsonMapper.builder().changeDefaultPropertyInclusion(ignored -> inclusion)
                .enable(SerializationFeature.APPLY_JSON_INCLUDE_FOR_CONTAINERS,
                        SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
    }

    private String filtered(ObjectWriter writer, Object value) {
        TokenFilter filter = new TokenFilter() {
            @Override
            public TokenFilter includeProperty(String name) {
                return "after".equals(name) ? INCLUDE_ALL : this;
            }

            @Override
            protected boolean _includeScalar() { return false; }

            @Override
            public boolean includeEmptyArray(boolean contentsFiltered) { return true; }
        };
        StringWriter output = new StringWriter();
        try (JsonGenerator g = new FilteringGeneratorDelegate(writer.createGenerator(output),
                filter, TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)) {
            writer.writeValue(g, new Bean<>(value));
        }
        return output.toString();
    }
}
