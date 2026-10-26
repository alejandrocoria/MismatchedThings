package games.alejandrocoria.mismatchedboots.runtimetest.tidehead;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Set;

/** Own verifier and constants, independent even when another owner's verifier publishes true. */
public final class HeadPlugin implements IMixinConfigPlugin, IExtension {
    @Override
    public void onLoad(String mixinPackage) {
        if (!TideOptionalApi.inspect().available()) {
            return;
        }
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!TideOptionalApi.inspect().available()) {
            return false;
        }
        return !mixin.endsWith("HeadResultMixin") || !System.getProperty("mismatchedboots.runtimetest.tideOwners", "").equals("missingHead");
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(TideEntryShape.ITEM)) {
            if (TideEntryShape.prepareGetter(node, "tideHead$baseReady") && TideEntryShape.baseFailure(node) == null) {
                TideEntryShape.publish(node, "tideHead$baseReady");
            }
        } else if (node.name.equals(TideEntryShape.ARMOR)) {
            if (TideEntryShape.prepareGetter(node, "tideHead$entryReady") && TideEntryShape.entryFailure(node, "HEAD") == null) {
                TideEntryShape.publish(node, "tideHead$entryReady");
            }
        }
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
    }

    @Override
    public void postApply(ITargetClassContext context) {
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
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
}
