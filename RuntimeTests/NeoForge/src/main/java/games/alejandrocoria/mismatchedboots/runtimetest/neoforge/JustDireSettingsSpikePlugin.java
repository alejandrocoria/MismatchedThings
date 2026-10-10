package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Explicit opt-in, optional client-only experiment. No production mixin is changed. */
public final class JustDireSettingsSpikePlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireSettingsVariant", "live");
        return Boolean.getBoolean("mismatchedboots.runtimetest.justDireSettingsSpike")
                && !variant.equals("uiDisabled") && !variant.equals("absent");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
