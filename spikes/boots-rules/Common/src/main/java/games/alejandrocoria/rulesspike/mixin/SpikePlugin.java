package games.alejandrocoria.rulesspike.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** No candidate injection into the historical binary; only its automated window is hidden. */
public final class SpikePlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String target, String mixin) {
        return mixin.endsWith(".WindowMixin") || Boolean.getBoolean("rules.spike.candidate");
    }

    public void acceptTargets(Set<String> targets, Set<String> otherTargets) {
    }

    public List<String> getMixins() {
        return null;
    }

    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
