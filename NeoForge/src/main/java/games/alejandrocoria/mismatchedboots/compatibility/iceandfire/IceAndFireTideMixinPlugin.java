package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

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

/** Tide owns its proof and publication; damage/Troll/Lightning guards are independent. */
public final class IceAndFireTideMixinPlugin implements IMixinConfigPlugin, IExtension {
    @Override
    public void onLoad(String mixinPackage) {
        if (!IceAndFireTideApi.inspect().available()) { return; }
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            // Inspected .99/.114 lifecycle: onLoad precedes Extensions.select. export runs after
            // MixinExtras sugar/coprocessors even with DEBUG_EXPORT off. Missing registration
            // leaves getters false; this is not a promise about later external transformers.
            extensions.add(this);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) { return IceAndFireTideApi.inspect().available(); }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(IceAndFireTideShape.ITEM)) {
            String getter = "mismatchedboots$tideBaseReady";
            if (IceAndFireTideShape.prepareGetter(node, getter) && IceAndFireTideShape.baseFailure(node) == null) {
                IceAndFireTideShape.publish(node, getter);
            }
        } else if (node.name.equals(IceAndFireTideShape.ARMOR)) {
            String getter = "mismatchedboots$tideEntryReady";
            if (IceAndFireTideShape.prepareGetter(node, getter) && IceAndFireTideShape.entryFailure(node) == null) {
                IceAndFireTideShape.publish(node, getter);
            }
        }
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
