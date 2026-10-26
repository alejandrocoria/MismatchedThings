package games.alejandrocoria.mismatchedboots.compatibility.advancednetherite;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedNetheriteMixinPluginTest {
    private static final String STACK_PREDICATE = "(Lnet/minecraft/world/item/ItemStack;)Z";
    private static final String OPTIONAL_PACKAGE = "com/autovw/advancednetherite";

    @Test
    void acceptsOnlyTheExactExpectedApi() {
        assertTrue(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), armor()));
    }

    @Test
    void leavesUnrelatedMixinsEnabledWithoutInspectingTheOptionalApi() {
        var plugin = new AdvancedNetheriteMixinPlugin();

        assertTrue(plugin.shouldApplyMixin("fixture.Target", "fixture.UnrelatedMixin"));
    }

    @Test
    void rejectsMissingClassesOrMethods() {
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(null, armor()));
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), null));

        ClassNode hooks = hooks();
        hooks.methods.removeIf(method -> method.name.equals("pacifyPhantoms"));
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks, armor()));

        ClassNode armor = armor();
        armor.methods.removeIf(method -> method.name.equals("pacifiesEndermen"));
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), armor));
    }

    @Test
    void rejectsWrongDescriptorsStaticMethodsAndNonPublicMethods() {
        ClassNode wrongDescriptor = hooks();
        method(wrongDescriptor, "pacifyEndermen").desc = "()Z";
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(wrongDescriptor, armor()));

        ClassNode staticMethod = armor();
        method(staticMethod, "pacifiesPhantoms").access |= Opcodes.ACC_STATIC;
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), staticMethod));

        ClassNode nonPublicMethod = hooks();
        method(nonPublicMethod, "pacifyPhantoms").access &= ~Opcodes.ACC_PUBLIC;
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(nonPublicMethod, armor()));
    }

    @Test
    void rejectsHookTypeThatIsNotAnInterface() {
        ClassNode hooks = hooks();
        hooks.access &= ~Opcodes.ACC_INTERFACE;

        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks, armor()));
    }

    @Test
    void rejectsInaccessibleOrWrongKindsOfApiTypes() {
        ClassNode inaccessibleHooks = hooks();
        inaccessibleHooks.access &= ~Opcodes.ACC_PUBLIC;
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(inaccessibleHooks, armor()));

        ClassNode inaccessibleArmor = armor();
        inaccessibleArmor.access &= ~Opcodes.ACC_PUBLIC;
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), inaccessibleArmor));

        ClassNode armorInterface = armor();
        armorInterface.access |= Opcodes.ACC_INTERFACE;
        assertFalse(AdvancedNetheriteMixinPlugin.hasCompatibleApi(hooks(), armorInterface));
    }

    @Test
    void pluginHasNoSymbolicAdvancedNetheriteReferences() throws IOException {
        String resource = "/" + AdvancedNetheriteMixinPlugin.class.getName().replace('.', '/') + ".class";
        try (InputStream input = AdvancedNetheriteMixinPlugin.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            AtomicBoolean found = new AtomicBoolean();
            new ClassReader(input).accept(symbolicReferenceVisitor(found), ClassReader.SKIP_DEBUG);
            assertFalse(found.get(), "Plugin bytecode links Advanced Netherite instead of naming it as data");
        }
    }

    private static ClassNode hooks() {
        ClassNode node = type("fixture/Hooks", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT);
        node.methods.add(predicate("pacifyEndermen"));
        node.methods.add(predicate("pacifyPhantoms"));
        return node;
    }

    private static ClassNode armor() {
        ClassNode node = type("fixture/Armor", Opcodes.ACC_PUBLIC);
        node.methods.add(predicate("pacifiesEndermen"));
        node.methods.add(predicate("pacifiesPhantoms"));
        return node;
    }

    private static ClassNode type(String name, int access) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V25;
        node.access = access;
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static MethodNode predicate(String name) {
        return new MethodNode(Opcodes.ACC_PUBLIC, name, STACK_PREDICATE, null, null);
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                            .filter(method -> method.name.equals(name))
                            .findFirst()
                            .orElseThrow();
    }

    private static ClassVisitor symbolicReferenceVisitor(AtomicBoolean found) {
        return new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                check(found, signature, superName);
                if (interfaces != null) {
                    for (String type : interfaces) {
                        check(found, type);
                    }
                }
            }

            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                check(found, descriptor, signature);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                check(found, descriptor, signature);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        check(found, exception);
                    }
                }
                return methodReferenceVisitor(found);
            }
        };
    }

    private static MethodVisitor methodReferenceVisitor(AtomicBoolean found) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                check(found, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                check(found, owner, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                check(found, owner, descriptor);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                check(found, descriptor, bootstrap.getOwner(), bootstrap.getDesc());
                for (Object argument : arguments) {
                    if (argument instanceof Type type) {
                        check(found, type.getDescriptor());
                    } else if (argument instanceof Handle handle) {
                        check(found, handle.getOwner(), handle.getDesc());
                    }
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type type) {
                    check(found, type.getDescriptor());
                } else if (value instanceof Handle handle) {
                    check(found, handle.getOwner(), handle.getDesc());
                }
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                check(found, descriptor);
            }
        };
    }

    private static void check(AtomicBoolean found, String... references) {
        for (String reference : references) {
            if (reference != null && reference.contains(OPTIONAL_PACKAGE)) {
                found.set(true);
            }
        }
    }
}
