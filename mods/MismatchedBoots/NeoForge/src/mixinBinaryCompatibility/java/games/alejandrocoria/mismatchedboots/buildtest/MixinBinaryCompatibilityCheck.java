package games.alejandrocoria.mismatchedboots.buildtest;

import com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.util.Annotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

/** Checks the publication itself, not classes recompiled for the runtime under test. */
public final class MixinBinaryCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path publication = Path.of(args[0]);
        var transformer = new FactoryRedirectWrapperMixinTransformer();
        int redirects = 0;
        boolean incompatibleControlRejected = false;

        try (var archive = new JarFile(publication.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                var node = new ClassNode();
                try (var stream = archive.getInputStream(entry)) {
                    new ClassReader(stream).accept(node, 0);
                }

                int classRedirects = countCompatibleRedirects(node);
                if (classRedirects == 0) {
                    continue;
                }

                if (!incompatibleControlRejected) {
                    requireIncompatibleControlRejected(node, transformer);
                    incompatibleControlRejected = true;
                }

                transformer.transform(null, node);
                redirects += classRedirects;
            }
        }

        if (redirects == 0 || !incompatibleControlRejected) {
            throw new AssertionError("The published armor Redirect and its incompatible-format control were not exercised");
        }

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(publication)));
        System.out.printf("MixinExtras 0.5.4: %d published Redirect(s) passed; array-format control rejected; SHA-256=%s%n",
                redirects, hash);
    }

    private static int countCompatibleRedirects(ClassNode node) {
        int redirects = 0;
        for (var method : node.methods) {
            AnnotationNode redirect = Annotations.getVisible(method, Redirect.class);
            if (redirect == null) {
                continue;
            }

            Object at = Annotations.getValue(redirect, "at");
            if (!(at instanceof AnnotationNode)) {
                throw new AssertionError("Published @Redirect.at is not compatible with MixinExtras 0.5.4: "
                        + node.name + "." + method.name);
            }
            redirects++;
        }
        return redirects;
    }

    private static void requireIncompatibleControlRejected(ClassNode original,
                                                           FactoryRedirectWrapperMixinTransformer transformer) {
        var incompatible = new ClassNode();
        original.accept(incompatible);
        for (var method : incompatible.methods) {
            AnnotationNode redirect = Annotations.getVisible(method, Redirect.class);
            if (redirect != null) {
                AnnotationNode at = Annotations.getValue(redirect, "at");
                // Reproduce the compiler's newer binary format without retaining a second broken artifact.
                Annotations.setValue(redirect, "at", List.of(at));
            }
        }

        try {
            transformer.transform(null, incompatible);
        } catch (ClassCastException expected) {
            return;
        }
        throw new AssertionError("MixinExtras 0.5.4 did not reject the incompatible Redirect format");
    }
}
