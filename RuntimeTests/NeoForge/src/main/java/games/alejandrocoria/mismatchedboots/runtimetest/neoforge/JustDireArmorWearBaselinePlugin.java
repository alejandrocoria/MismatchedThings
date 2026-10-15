package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Remove the actual production injection target in one isolated JVM; preserve ordinary delegation. */
public final class JustDireArmorWearBaselinePlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return System.getProperty("mismatchedboots.runtimetest.justDireArmorWearVariant", "").equals("missing");
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        int changed = 0;
        for (var method : node.methods) {
            if (!method.name.equals("lambda$onArmorHurt$0")) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("hurtAndBreak")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/NeoForgeJustDireArmorWearTest";
                    call.name = "ordinary";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V";
                    changed++;
                }
            }
        }
        if (changed != 1) { throw new IllegalStateException("Missing-consumer fault did not remove the real target"); }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
