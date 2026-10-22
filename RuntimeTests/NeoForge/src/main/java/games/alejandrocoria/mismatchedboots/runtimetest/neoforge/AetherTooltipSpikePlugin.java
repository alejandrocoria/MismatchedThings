package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** The prototype cannot affect another fixture or a production installation. */
public final class AetherTooltipSpikePlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("mismatchedboots.runtimetest.aetherIITooltip");
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() {
        // Omitting the optional target before Mixin parses it also avoids a missing-class warning.
        return Boolean.getBoolean("mismatchedboots.runtimetest.aetherIITooltip")
                && !Boolean.getBoolean("mismatchedboots.runtimetest.aetherTooltipAbsent")
                ? List.of("AetherTooltipNativeProbeMixin") : List.of();
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
