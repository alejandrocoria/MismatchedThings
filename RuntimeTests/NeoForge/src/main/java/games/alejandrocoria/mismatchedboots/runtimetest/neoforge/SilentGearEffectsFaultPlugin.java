package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Controlled real competing transformations, before production final-export readiness publication. */
public final class SilentGearEffectsFaultPlugin implements IMixinConfigPlugin, Opcodes {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearStatsSpike")
                && List.of("repair", "wear", "compatible").contains(System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", ""));
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant");
        for (MethodNode method : node.methods) {
            if (method.name.equals("inventoryTick") || method.name.equals("onDurabilityDamage")) {
                method.instructions.insert(new InsnNode(NOP));
                for (var instruction : method.instructions) {
                    if (variant.equals("repair") && instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/silentchaos512/gear/util/GearHelper") && call.name.equals("attemptDamage")) {
                        call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/SilentGearEffectsTest";
                        call.name = "ignoredNativeRepair";
                    }
                    if (variant.equals("wear") && method.name.equals("onDurabilityDamage") && instruction.getOpcode() == IADD) {
                        method.instructions.set(instruction, new InsnNode(ISUB));
                    }
                }
            }
        }
        var marker = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$modified", "()Z", null, null);
        marker.instructions.add(new InsnNode(ICONST_1)); marker.instructions.add(new InsnNode(IRETURN)); marker.maxStack = 1;
        node.methods.add(marker);
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
