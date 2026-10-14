package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Opt-in research seams; normal runtime/production launches never acquire these methods. */
public final class JustDireLavaRepairSpikePlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairTest")) {
            return mixin.endsWith("BlockMixin");
        }
        return Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairSpike");
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
