package cloud.dcrivella.clientserver;

import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.Writer;
import java.lang.reflect.Method;
import java.text.DateFormat;

/**
 * Registers the Jackson reflection metadata required by the client server native image.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class ClientServerRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Registers reflective Jackson builder and serialization methods used when rendering token claims.
     *
     * @param hints native runtime metadata registry
     * @param classLoader application class loader supplied by Spring
     * @throws IllegalStateException when a required Jackson method is unavailable
     */
    @Override
    public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
        try {
            register(hints, JsonMapper.class.getMethod("builder"));
            register(hints, MapperBuilder.class.getMethod("defaultDateFormat", DateFormat.class));
            register(hints, MapperBuilder.class.getMethod("disable", SerializationFeature[].class));
            register(hints, MapperBuilder.class.getMethod("findAndAddModules"));
            register(hints, MapperBuilder.class.getMethod("build"));
            register(hints, JsonMapper.Builder.class.getMethod("build"));
            register(hints, ObjectMapper.class.getMethod("writeValue", Writer.class, Object.class));
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Jackson reflection hints are required for native image support", ex);
        }
    }

    /**
     * Marks one reflected Jackson method as invokable in the native image.
     *
     * @param hints native runtime metadata registry
     * @param method reflected method used by Jackson
     */
    private static void register(RuntimeHints hints, Method method) {
        hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
    }
}
