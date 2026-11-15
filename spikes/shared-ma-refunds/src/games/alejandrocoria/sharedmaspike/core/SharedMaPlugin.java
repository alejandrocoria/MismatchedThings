package games.alejandrocoria.sharedmaspike.core;

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

/** Real entry/final proof, derived mechanically from the maintained observer proof. */
public final class SharedMaPlugin implements IMixinConfigPlugin, IExtension {
    private SharedMaShape shape;

    public void onLoad(String name) {
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            provider.getClassNode("com.blakebr0.mysticalagriculture.api.util.AugmentUtils");
            var stack = provider.getClassNode("net.minecraft.world.item.ItemStack");
            var candidate = new SharedMaShape(stack.methods.stream().noneMatch(method ->
                    method.name.equals("applyDamage") && method.desc.contains("/LivingEntity;")));
            var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (!candidate.entry(stack) || !(transformer instanceof IMixinTransformer mixin)
                    || !(mixin.getExtensions() instanceof Extensions extensions)) {
                throw new IllegalStateException("Shared MA entry refused");
            }
            shape = candidate;
            extensions.add(this);
        } catch (ClassNotFoundException absent) {
            // Optional absence: no native types are loaded or initialized.
        } catch (Exception failure) {
            throw new IllegalStateException("Shared MA candidate admission failed", failure);
        }
    }

    public boolean shouldApplyMixin(String target, String mixin) { return shape != null; }
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (shape != null && node.name.equals("net/minecraft/world/item/ItemStack")) {
            boolean ready = shape.publish(node);
            System.setProperty("spike.ma.finalReady", Boolean.toString(ready));
            if (!ready) {
                org.slf4j.LoggerFactory.getLogger("shared-ma-spike").warn("Shared MA candidate flow refused");
            }
        }
    }
    public boolean checkActive(MixinEnvironment environment) { return true; }
    public void preApply(ITargetClassContext context) {}
    public void postApply(ITargetClassContext context) {}
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
}
