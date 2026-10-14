package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Paired Jump routing and independent local Negate spending permission. */
public final class JustDireJumpMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;
    private boolean negateAvailable;

    @Override
    public void onLoad(String mixinPackage) {
        boolean present = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode handler = provider.getClassNode(JustDireJumpShape.HANDLER.replace('/', '.'));
            present = true;
            available = JustDireJumpShape.entries(handler) && JustDireJumpShape.api(
                    provider.getClassNode(JustDireJumpShape.TOOL.replace('/', '.')),
                    provider.getClassNode(JustDireJumpShape.HELPERS.replace('/', '.')),
                    provider.getClassNode(JustDireJumpShape.ACTION.replace('/', '.')));
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (available && transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(this);
            } else { available = false; }
            if (!available) { Constants.LOG.warn("Just Dire Things Jump local contract/API incompatible; compatibility disabled"); }
            if (available) {
                try {
                    negateAvailable = JustDireNegateFallShape.flow(handler, false) && JustDireNegateFallShape.api(
                            provider.getClassNode(JustDireJumpShape.TOOL.replace('/', '.')),
                            provider.getClassNode(JustDireJumpShape.ABILITY.replace('/', '.')));
                    if (!negateAvailable) { Constants.LOG.warn("Just Dire Things Negate Fall local contract/API incompatible; compatibility disabled"); }
                } catch (Exception | LinkageError failure) {
                    Constants.LOG.warn("Could not inspect Just Dire Things Negate Fall API; compatibility disabled", failure);
                }
            }
        } catch (ClassNotFoundException failure) {
            available = false;
            if (present) { Constants.LOG.warn("Just Dire Things Jump API incomplete; compatibility disabled", failure); }
        } catch (Exception | LinkageError failure) {
            available = false;
            Constants.LOG.warn("Could not inspect Just Dire Things Jump API; compatibility disabled", failure);
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return available; }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(JustDireJumpShape.HANDLER)) {
            boolean jump = JustDireJumpShape.publish(node);
            if (!jump) { Constants.LOG.warn("Just Dire Things Jump transformed paired hooks incompatible; compatibility disabled"); }
            boolean negate = JustDireNegateFallShape.publish(node, jump && negateAvailable);
            if (jump && negateAvailable && !negate) {
                Constants.LOG.warn("Just Dire Things Negate Fall transformed flow incompatible; compatibility disabled");
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
