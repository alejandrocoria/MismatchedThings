package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** No attribute transformations in storage-only, production or unrelated fixture runs. */
public final class JustDireAttributeSpikePlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributeSpike")
                && !Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributeHookAbsent");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
