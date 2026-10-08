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

/** One paired local permission; no dependency on the independent Ice and Fire integrations. */
public final class JustDireJumpMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;

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
            if (!available) { Constants.LOG.warn("Just Dire Things passive Jump local contract/API incompatible; compatibility disabled"); }
        } catch (ClassNotFoundException failure) {
            available = false;
            if (present) { Constants.LOG.warn("Just Dire Things passive Jump API incomplete; compatibility disabled", failure); }
        } catch (Exception | LinkageError failure) {
            available = false;
            Constants.LOG.warn("Could not inspect Just Dire Things passive Jump API; compatibility disabled", failure);
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return available; }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(JustDireJumpShape.HANDLER) && !JustDireJumpShape.publish(node)) {
            Constants.LOG.warn("Just Dire Things passive Jump transformed paired hooks incompatible; compatibility disabled");
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
