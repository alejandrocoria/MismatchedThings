package games.alejandrocoria.mismatchedleggings.test.undergarden;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SpikePlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("leggings.test.undergardenSpike") || !Boolean.getBoolean("undergarden.spike.candidate")) {
            return false;
        }
        String graph = System.getProperty("undergarden.spike.graph");
        return (!mixin.endsWith("BootsSourceCandidateMixin") || !graph.equals("leggings"))
                && (!mixin.endsWith("LeggingsSourceCandidateMixin") || !graph.equals("boots"));
    }

    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
