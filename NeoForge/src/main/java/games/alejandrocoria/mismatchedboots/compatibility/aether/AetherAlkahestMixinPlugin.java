package games.alejandrocoria.mismatchedboots.compatibility.aether;

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

/** Apply each optional domain only after ordinary peer mixins have completed. */
public final class AetherAlkahestMixinPlugin implements IMixinConfigPlugin, IExtension {
    @Override public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        try { MixinService.getService().getBytecodeProvider().getClassNode(target); return true; }
        catch (ClassNotFoundException absent) { return false; }
        catch (Exception | LinkageError failure) { AetherAlkahestProtection.disable(target.endsWith("AetherIIEventListeners"), failure); return false; }
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(AetherAlkahestShape.FLUID) && !node.name.equals(AetherAlkahestShape.EVENTS)) { return; }
        boolean armor = node.name.equals(AetherAlkahestShape.EVENTS);
        try { if (!AetherAlkahestShape.apply(node)) { AetherAlkahestProtection.disable(armor, null); } }
        catch (Exception | LinkageError failure) { AetherAlkahestProtection.disable(armor, failure); }
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
