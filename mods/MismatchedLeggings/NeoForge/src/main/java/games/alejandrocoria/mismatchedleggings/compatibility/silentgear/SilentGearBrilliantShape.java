package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Independent read-only native armor callback, without linking native classes. */
public final class SilentGearBrilliantShape implements Opcodes {
    public static final String OWNER = SilentGearSetShape.PREFIX + "item/gear/GearArmorItem";

    private SilentGearBrilliantShape() {}

    public static boolean ready(ClassNode owner) {
        var methods = owner.methods.stream().filter(m -> m.name.equals("makesPiglinsNeutral")
                && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")).toList();
        if (methods.size() != 1 || !methods.getFirst().tryCatchBlocks.isEmpty()) { return false; }
        var code = SilentGearSetShape.code(methods.getFirst().instructions);
        String prefix = SilentGearSetShape.PREFIX;
        String resource = "L" + prefix + "api/util/DataResource;";
        return code.size() == 4 && code.get(0) instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == 1
                && code.get(1) instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC
                && field.owner.equals(prefix + "util/Const$Traits") && field.name.equals("BRILLIANT") && field.desc.equals(resource)
                && code.get(2) instanceof MethodInsnNode query && query.getOpcode() == INVOKESTATIC
                && query.owner.equals(prefix + "util/TraitHelper") && query.name.equals("hasTrait")
                && query.desc.equals("(Lnet/minecraft/world/item/ItemInstance;" + resource + ")Z")
                && code.get(3).getOpcode() == IRETURN;
    }
}
