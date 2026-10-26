package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Insert a real second ItemStack.is into the native method, before production injection prepares. */
public final class BeltExtraPredicatePlugin implements IMixinConfigPlugin {
    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("BeltExtraPredicateMixin")) {
            return;
        }

        MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals("entityInside"))
                                        .findFirst().orElseThrow();
        String player = "net/minecraft/world/entity/player/Player";
        String slot = "net/minecraft/world/entity/EquipmentSlot";
        InsnList extra = new InsnList();
        LabelNode skip = new LabelNode();
        extra.add(new VarInsnNode(Opcodes.ALOAD, 4));
        extra.add(new TypeInsnNode(Opcodes.INSTANCEOF, player));
        extra.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        extra.add(new VarInsnNode(Opcodes.ALOAD, 4));
        extra.add(new TypeInsnNode(Opcodes.CHECKCAST, player));
        extra.add(new FieldInsnNode(Opcodes.GETSTATIC, slot, "FEET", "L" + slot + ";"));
        extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, player, "getItemBySlot",
                                     "(L" + slot + ";)Lnet/minecraft/world/item/ItemStack;", false));
        extra.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/item/Items", "DIAMOND",
                                    "Lnet/minecraft/world/item/Item;"));
        extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack",
                                     "is", "(Ljava/lang/Object;)Z", false));
        extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                     "games/alejandrocoria/mismatchedboots/runtimetest/createfly/fabric/BeltExtraPredicateFixture",
                                     "observe", "(Z)V", false));
        extra.add(skip);
        method.instructions.insert(extra);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
