package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

/** Selection only: inspecting bytecode never grants final entry/base permission or loads optional classes. */
final class IceAndFireTideApi {
    record Result(boolean present, boolean available, String reason) {
    }

    private static Result cached;

    static synchronized Result inspect() {
        if (cached != null) {
            return cached;
        }
        boolean present = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode armor = provider.getClassNode(IceAndFireTideShape.ARMOR.replace('/', '.'));
            present = true;
            ClassNode item = provider.getClassNode(IceAndFireTideShape.ITEM.replace('/', '.'));
            ClassNode type = provider.getClassNode("net.minecraft.world.item.equipment.ArmorType");
            boolean valid = IceAndFireTideShape.ITEM.equals(armor.superName)
                    && armor.fields.stream().anyMatch(field -> field.name.equals("type")
                    && field.desc.equals("Lnet/minecraft/world/item/equipment/ArmorType;")
                    && (field.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC))
                    == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))
                    && publicInstanceMethod(armor, "inventoryTick", IceAndFireTideShape.TICK)
                    && publicInstanceMethod(item, "inventoryTick", IceAndFireTideShape.TICK)
                    && publicInstanceMethod(type, "getSlot", "()Lnet/minecraft/world/entity/EquipmentSlot;");
            cached = new Result(true, valid, valid ? "supported API" : "unsupported Tide API");
        } catch (ClassNotFoundException failure) {
            cached = new Result(present, false, present ? "incomplete Tide API" : "Ice and Fire absent");
        } catch (Exception | LinkageError failure) {
            cached = new Result(true, false, "Tide API inspection failed: " + failure.getClass().getSimpleName());
        }
        return cached;
    }

    private static boolean publicInstanceMethod(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == Opcodes.ACC_PUBLIC);
    }
}
