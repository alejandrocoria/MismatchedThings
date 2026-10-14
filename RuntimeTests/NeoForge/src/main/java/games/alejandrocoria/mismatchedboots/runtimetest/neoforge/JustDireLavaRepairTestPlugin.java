package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class JustDireLavaRepairTestPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean enabled = Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairClientTest");
        if (mixin.endsWith("SettingsFailureMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairSettingsFailure");
        }
        if (mixin.endsWith("ApiMixin")) {
            String variant = System.getProperty("mismatchedboots.runtimetest.justDireLavaRepairVariant", "");
            return enabled && (variant.equals("apiMissing") || variant.equals("particlesMissing"));
        }
        return enabled;
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("ApiMixin")) { return; }
        boolean particles = System.getProperty("mismatchedboots.runtimetest.justDireLavaRepairVariant", "").equals("particlesMissing");
        String original = particles ? "com.direwolf20.justdirethings.client.particles.itemparticle.ItemFlowParticleData"
                : "com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool";
        int replaced = 0;
        for (var method : node.methods) {
            for (var insn : method.instructions) {
                if (insn instanceof LdcInsnNode constant && original.equals(constant.cst)) {
                    constant.cst = original + "Unavailable"; replaced++;
                }
            }
        }
        if (replaced != 1) { throw new IllegalStateException("Optional Lava API fault did not target one binding"); }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
