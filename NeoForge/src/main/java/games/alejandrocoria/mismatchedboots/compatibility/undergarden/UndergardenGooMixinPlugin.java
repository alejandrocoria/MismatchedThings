package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

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
import java.util.concurrent.atomic.AtomicBoolean;

public final class UndergardenGooMixinPlugin implements IMixinConfigPlugin, IExtension {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    @Override public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        try { MixinService.getService().getBytecodeProvider().getClassNode(target); return true; }
        catch (ClassNotFoundException absent) { return false; }
        catch (Exception | LinkageError failure) { warn(failure); return false; }
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(UndergardenGooShape.TARGET)) { return; }
        try { if (!UndergardenGooShape.apply(node)) { warn(null); } }
        catch (Exception | LinkageError failure) { warn(failure); }
    }
    private static void warn(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            Constants.LOG.warn("Undergarden mixed goo protection disabled: incompatible local predicate", failure);
        }
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
