package games.alejandrocoria.mismatchedleggings.compatibility.createfly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateFlyMixinPluginTest {
    private static final String ARMOR = "com/zurrtum/create/content/equipment/armor/CardboardArmorItem";

    @Test
    void acceptsTheUsedBoundaryWithoutFingerprintingOtherHooks() {
        var api = compatibleApi();
        assertTrue(compatible(api));
        // A sibling's extra RETURN path must not invalidate the local INSTANCEOF anchor.
        api[0].methods.getFirst().instructions.add(new InsnNode(Opcodes.ICONST_1));
        api[0].methods.getFirst().instructions.add(new InsnNode(Opcodes.IRETURN));
        api[0].methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "unrelated", "()V", null, null));
        api[2].fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "unrelated", "I", null, null));
        assertTrue(compatible(api));
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent_handler", "absent_armor", "absent_items", "private_class", "interface",
            "missing_predicate", "descriptor", "instance_method", "private_method", "missing_anchor",
            "wrong_anchor", "wrong_opcode", "missing_item", "wrong_item", "wrong_item_type", "mutable_item"})
    void rejectsChangesToTheRequiredLocalBoundary(String change) {
        var api = compatibleApi();
        switch (change) {
            case "absent_handler" -> api[0] = null;
            case "absent_armor" -> api[1] = null;
            case "absent_items" -> api[2] = null;
            case "private_class" -> api[0].access = 0;
            case "interface" -> api[1].access |= Opcodes.ACC_INTERFACE;
            case "missing_predicate" -> api[0].methods.clear();
            case "descriptor" -> api[0].methods.getFirst().desc = "(Ljava/lang/Object;)Z";
            case "instance_method" -> api[0].methods.getFirst().access = Opcodes.ACC_PUBLIC;
            case "private_method" -> api[0].methods.getFirst().access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
            case "missing_anchor" -> api[0].methods.getFirst().instructions.clear();
            case "wrong_anchor" -> ((TypeInsnNode) api[0].methods.getFirst().instructions.getFirst()).desc = "other/Armor";
            case "wrong_opcode" -> api[0].methods.getFirst().instructions.set(
                    api[0].methods.getFirst().instructions.getFirst(), new TypeInsnNode(Opcodes.CHECKCAST, ARMOR));
            case "missing_item" -> api[2].fields.clear();
            case "wrong_item" -> api[2].fields.getFirst().name = "CARDBOARD_BOOTS";
            case "wrong_item_type" -> api[2].fields.getFirst().desc = "Lnet/minecraft/world/item/Item;";
            case "mutable_item" -> api[2].fields.getFirst().access &= ~Opcodes.ACC_FINAL;
            default -> throw new AssertionError(change);
        }
        assertFalse(compatible(api));
    }

    private static boolean compatible(ClassNode[] api) {
        return CreateFlyMixinPlugin.hasCompatibleCardboardApi(api[0], api[1], api[2]);
    }

    private static ClassNode[] compatibleApi() {
        var handler = publicClass();
        var predicate = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "isCardboardArmor", "(Lnet/minecraft/world/item/ItemStack;)Z", null, null);
        predicate.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, ARMOR));
        predicate.instructions.add(new InsnNode(Opcodes.IRETURN));
        handler.methods.add(predicate);
        var items = publicClass();
        items.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "CARDBOARD_LEGGINGS", "L" + ARMOR + ";", null, null));
        return new ClassNode[]{handler, publicClass(), items};
    }

    private static ClassNode publicClass() {
        var node = new ClassNode();
        node.access = Opcodes.ACC_PUBLIC;
        return node;
    }
}
