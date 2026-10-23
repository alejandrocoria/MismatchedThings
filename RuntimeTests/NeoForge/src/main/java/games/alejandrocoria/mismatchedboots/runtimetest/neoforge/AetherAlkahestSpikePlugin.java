package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** No native targets are requested outside the explicitly selected candidate server. */
public final class AetherAlkahestSpikePlugin implements IMixinConfigPlugin {
    @Override public List<String> getMixins() {
        return System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "").equals("candidate")
                ? List.of("AetherAlkahestFluidSpikeMixin", "AetherAlkahestArmorSpikeMixin", "AetherAlkahestTagSpikeMixin") : List.of();
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
