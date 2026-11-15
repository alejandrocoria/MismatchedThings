package games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture;

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

/** Optional admission/final proof; never resolves MA bindings or initializes game classes. */
public final class MysticalRefundMixinPlugin implements IMixinConfigPlugin, IExtension {
    private MysticalRefundShape shape;
    private boolean warned;

    @Override
    public void onLoad(String mixinPackage) {
        boolean present = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            provider.getClassNode("com.blakebr0.mysticalagriculture.api.util.AugmentUtils");
            present = true;
            var stack = provider.getClassNode(MysticalRefundShape.OWNER.replace('/', '.'));
            boolean fabric = stack.methods.stream().noneMatch(method ->
                    method.name.equals("applyDamage") && method.desc.contains("/LivingEntity;"));
            var candidate = new MysticalRefundShape(fabric);
            var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (candidate.entry(stack) && transformer instanceof IMixinTransformer mixin
                    && mixin.getExtensions() instanceof Extensions extensions) {
                shape = candidate;
                extensions.add(this);
            } else {
                warn();
            }
        } catch (ClassNotFoundException absent) {
            if (present) {
                warn();
            }
        } catch (Exception | LinkageError failure) {
            warn();
        }
    }

    private void warn() {
        if (!warned) {
            warned = true;
            org.slf4j.LoggerFactory.getLogger("mismatchedthings").warn(
                    "Mystical Agriculture refund flow incompatible; ordinary breakage retained without augment refunds");
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return shape != null;
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (shape != null && node.name.equals(MysticalRefundShape.OWNER) && !shape.publish(node)) {
            warn();
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
