package games.alejandrocoria.mismatchedthings.api.compatibility;

import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeInfusionService;
import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityServiceLoadingTest {
    @TempDir
    Path directory;

    static List<Arguments> services() {
        return List.of(
                Arguments.of(ForbiddenModifierService.class, ForbiddenProvider.class),
                Arguments.of(NeoVitaeInfusionService.class, NeoVitaeProvider.class),
                Arguments.of(RogdoriumInfusionService.class, RogdoriumProvider.class));
    }

    @ParameterizedTest
    @MethodSource("services")
    void discoversProviderThroughItsDefiningLoaderDespiteHostileThreadContext(Class<?> service, Class<?> provider) throws Exception {
        Path descriptor = directory.resolve("provider.txt");
        Files.writeString(descriptor, provider.getName() + "\n");
        var loader = new ServiceCopyLoader(service, provider, descriptor.toUri().toURL());

        Object instance = initializeWithHostileContext(service, loader);

        assertSame(loader.loadClass(provider.getName()), instance.getClass());
    }

    @ParameterizedTest
    @MethodSource("services")
    void keepsOptionalFallbackWhenItsDefiningLoaderHasNoProvider(Class<?> service, Class<?> provider) throws Exception {
        var loader = new ServiceCopyLoader(service, provider, null);

        Object instance = initializeWithHostileContext(service, loader);

        assertTrue(loader.loadClass(service.getName()).isInstance(instance));
        assertSame(loader, instance.getClass().getClassLoader());
    }

    private static Object initializeWithHostileContext(Class<?> service, ClassLoader loader) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                throw new AssertionError("Service lookup must not consult the caller's context loader: " + name);
            }
        });
        try {
            // A fresh copy exercises INSTANCE initialization even if another test used the original API.
            Class<?> copy = Class.forName(service.getName(), true, loader);
            return copy.getField("INSTANCE").get(null);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    // Copy only the API/provider/no-op classes; Minecraft dependencies keep their normal loader.
    private static final class ServiceCopyLoader extends ClassLoader {
        private final String service;
        private final String provider;
        private final URL descriptor;

        private ServiceCopyLoader(Class<?> service, Class<?> provider, URL descriptor) {
            super(CompatibilityServiceLoadingTest.class.getClassLoader());
            this.service = service.getName();
            this.provider = provider.getName();
            this.descriptor = descriptor;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(service) && !name.startsWith(service + "$") && !name.equals(provider)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = input.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.equals("META-INF/services/" + service)) {
                return Collections.enumeration(descriptor == null ? List.of() : List.of(descriptor));
            }
            return super.getResources(name);
        }
    }

    public static final class ForbiddenProvider implements ForbiddenModifierService {}

    public static final class NeoVitaeProvider implements NeoVitaeInfusionService {}

    public static final class RogdoriumProvider implements RogdoriumInfusionService {}
}
