package games.alejandrocoria.mismatchedleggings.compatibility.createfly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateFlyTooltipConfigTest {
    @Test
    void acceptsOnlyTheRequiredConfigSurface() {
        var api = configApi();
        api[1].fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "unrelated", "I", null, null));
        assertTrue(compatible(api));
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "private_class", "accessor", "field_type", "static_field",
            "inheritance", "getter", "static_getter"})
    void rejectsIncompatibleConfigWithoutInspectingGameplay(String change) {
        var api = configApi();
        switch (change) {
            case "absent" -> api[0] = null;
            case "private_class" -> api[1].access = 0;
            case "accessor" -> api[0].methods.clear();
            case "field_type" -> api[1].fields.getFirst().desc = "Ljava/lang/Boolean;";
            case "static_field" -> api[1].fields.getFirst().access |= Opcodes.ACC_STATIC;
            case "inheritance" -> api[2].superName = "java/lang/Object";
            case "getter" -> api[3].methods.clear();
            case "static_getter" -> api[3].methods.getFirst().access |= Opcodes.ACC_STATIC;
            default -> throw new AssertionError(change);
        }
        assertFalse(compatible(api));
    }

    private static boolean compatible(ClassNode[] api) {
        return CreateFlyMixinPlugin.hasCompatibleTooltipConfigApi(api[0], api[1], api[2], api[3]);
    }

    private static ClassNode[] configApi() {
        var configs = publicClass();
        configs.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "client",
                "()Lcom/zurrtum/create/client/infrastructure/config/CClient;", null, null));
        var client = publicClass();
        client.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "tooltips",
                "Lcom/zurrtum/create/catnip/config/ConfigBase$ConfigBool;", null, null));
        var bool = publicClass();
        bool.superName = "com/zurrtum/create/catnip/config/ConfigBase$CValue";
        var value = publicClass();
        value.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", null, null));
        return new ClassNode[]{configs, client, bool, value};
    }

    private static ClassNode publicClass() {
        var node = new ClassNode();
        node.access = Opcodes.ACC_PUBLIC;
        return node;
    }
}

