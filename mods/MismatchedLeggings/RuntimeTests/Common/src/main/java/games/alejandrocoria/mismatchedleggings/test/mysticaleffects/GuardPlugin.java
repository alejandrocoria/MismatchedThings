package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Prepares a runtime-identical opaque operand or an unrelated instruction before final production proofs. */
public final class GuardPlugin implements IMixinConfigPlugin, Opcodes {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String name) { return true; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() {
        return Boolean.getBoolean("leggings.test.mysticalEffects") && !Boolean.getBoolean("leggings.test.maAbsent") ? List.of("GuardTargets") : List.of();
    }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.maVariant", "normal");
        for (var method : node.methods) {
            if (variant.equals("compatible")) { method.instructions.insert(new InsnNode(NOP)); }
            if (variant.equals("armor") && node.name.endsWith("/LuckAugment") && method.name.equals("onArmorTick")) {
                var read = new InsnList(); read.add(new VarInsnNode(ALOAD, 1)); read.add(new InsnNode(POP)); method.instructions.insert(read);
            }
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) { continue; }
                if (variant.equals("equipment") && method.name.equals("getArmorAugments") && call.name.equals("getItemBySlot")) {
                    call.setOpcode(INVOKESTATIC); call.owner = "games/alejandrocoria/mismatchedleggings/test/mysticaleffects/GuardControl";
                    call.name = "opaqueSlot"; call.desc = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
                }
                if (variant.equals("health") && node.name.endsWith("/HealthBoostAugment") && call.owner.endsWith("/AbilityCache") && call.name.equals("add")) {
                    call.setOpcode(INVOKESTATIC); call.owner = "games/alejandrocoria/mismatchedleggings/test/mysticaleffects/GuardControl";
                    call.name = "opaqueAdd"; call.desc = "(Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/world/entity/player/Player;Ljava/lang/Runnable;)V";
                }
            }
        }
    }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
