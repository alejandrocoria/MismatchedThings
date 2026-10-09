package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Opt-in competing transformations of the actual consumer/binding, never production switches. */
public final class JustDireNegateGuardPlugin implements IMixinConfigPlugin, Opcodes {
    public static boolean rejectDebit;
    public static int rejectedAmount;
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireNegateGuard", "");
        if (mixin.endsWith("JustDireNegateDebitFailureMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.justDireNegateFallTest") && variant.isEmpty();
        }
        return mixin.endsWith("JustDireNegateApiMissingMixin") ? variant.equals("apiMissing")
                : variant.equals("consumerChanged") || variant.equals("publicationChanged");
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("JustDireNegateConsumerMixin")) { return; }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireNegateGuard");
        if (variant.equals("publicationChanged")) {
            MethodNode getter = node.methods.stream().filter(m -> m.name.equals("mismatchedboots$justDireNegateFallReady")).findFirst().orElseThrow();
            getter.instructions.clear(); getter.instructions.add(new InsnNode(ICONST_1)); getter.instructions.add(new InsnNode(IRETURN));
        } else {
            MethodNode fall = node.methods.stream().filter(m -> m.name.equals("LivingFallDamage")).findFirst().orElseThrow();
            for (var instruction : fall.instructions) {
                if (instruction.getOpcode() == D2I) {
                    InsnList changed = new InsnList(); changed.add(new InsnNode(ICONST_1)); changed.add(new InsnNode(IADD));
                    fall.instructions.insert(instruction, changed); return;
                }
            }
            throw new IllegalStateException("Native Negate multiplier not found");
        }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
