package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Invalid binding is opt-in; native JDT remains intact and other fixture runs are unaffected. */
public final class JustDireResourceFailurePlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("JustDireEnergyBarQueryFaultMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.justDireItemBarsTest")
                    && Set.of("queryFailure", "invalidWidth", "linkageFailure").contains(
                    System.getProperty("mismatchedboots.runtimetest.justDireItemBarsVariant", ""));
        }
        return System.getProperty("mismatchedboots.runtimetest.justDireResourcesGuard", "").equals("apiMissing");
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
