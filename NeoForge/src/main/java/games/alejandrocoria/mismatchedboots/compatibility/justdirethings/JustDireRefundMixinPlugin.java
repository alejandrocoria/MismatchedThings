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

public final class JustDireRefundMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available, warned;

    @Override public void onLoad(String mixinPackage) {
        boolean present = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            provider.getClassNode("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool"); present = true;
            var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            available = JustDireRefundShape.entry(provider.getClassNode(JustDireRefundShape.OWNER.replace('/', '.')))
                    && transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions;
            if (available) { ((Extensions) ((IMixinTransformer) transformer).getExtensions()).add(this); }
            else { warn(); }
        } catch (ClassNotFoundException absent) { if (present) { warn(); } }
        catch (Exception | LinkageError failure) { warn(); }
    }
    private void warn() {
        if (!warned) { warned = true; Constants.LOG.warn("Just Dire Things refund flow incompatible; ordinary breakage retained without upgrade refunds"); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return available; }
    @Override public void export(MixinEnvironment env, String name, boolean force, ClassNode node) {
        if (node.name.equals(JustDireRefundShape.OWNER) && !JustDireRefundShape.publish(node)) { warn(); }
    }
    @Override public boolean checkActive(MixinEnvironment env) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
