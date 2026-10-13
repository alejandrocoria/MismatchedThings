package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Opt-in Earthquake binding/query/settings failures; other JDT consumers remain native/live. */
public final class JustDireEarthquakeStateGuardPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireEarthquakeStateVariant", "live");
        return mixin.endsWith("JustDireEarthquakeApiMissingMixin") && variant.equals("apiMissing")
                || mixin.endsWith("JustDireEarthquakeQueryFailureMixin") && variant.equals("queryFailure")
                || mixin.endsWith("JustDireEarthquakeSettingsFailureMixin") && variant.equals("settingsFailure");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
