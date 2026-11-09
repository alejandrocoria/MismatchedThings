package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
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

public final class MysticalAugmentsMixinPlugin implements IMixinConfigPlugin, IExtension {

    private MysticalRefundShape refundShape;

    @Override
    public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public List<String> getMixins() {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(MysticalAugmentsShape.UTILS);
        } catch (ClassNotFoundException absent) {
            return List.of();
        } catch (Exception | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not inspect Mystical Agriculture augment API", failure);
            return List.of();
        }
        var selected = new java.util.ArrayList<>(List.of("MysticalEquipmentMixin", "MysticalArmorTickProofMixin", "MysticalHealthCleanupMixin"));
        try {
            var stack = MixinService.getService().getBytecodeProvider().getClassNode(MysticalRefundShape.OWNER);
            refundShape = new MysticalRefundShape(stack.methods.stream().noneMatch(m -> m.name.equals("applyDamage") && m.desc.contains("/LivingEntity;")));
            if (refundShape.entry(stack)) { selected.add("MysticalRefundMixin"); }
            else {
                refundShape = null;
                // Config discovery must not initialize game-facing helpers before mixins are prepared.
                org.slf4j.LoggerFactory.getLogger("mismatchedleggings").warn("Mystical Agriculture leggings refund flow incompatible; ordinary breakage retained without augment refunds");
            }
        } catch (Exception | LinkageError failure) {
            refundShape = null;
            org.slf4j.LoggerFactory.getLogger("mismatchedleggings").warn("Could not inspect Mystical Agriculture leggings refund boundary", failure);
        }
        return selected;
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(MysticalRefundShape.OWNER)) {
            if (refundShape != null && !refundShape.publish(node)) { MysticalRefundCompatibility.unavailable(null); }
        } else if (node.name.equals(MysticalAugmentsShape.UTILS)) {
            MysticalAugmentsShape.publish(node, MysticalAugmentsCompatibility.EQUIPMENT_PROOF, MysticalAugmentsShape.equipment(node));
        } else {
            if (node.name.equals(MysticalAugments.Family.HEALTH_BOOST.nativeClass().replace('.', '/'))) {
                MysticalAugmentsShape.publish(node, MysticalAugmentsCompatibility.HEALTH_PROOF, MysticalAugmentsShape.healthCleanup(node));
            }
            for (var family : MysticalAugments.Family.values()) {
                if (family.route == MysticalAugments.Route.ARMOR && node.name.equals(family.nativeClass().replace('.', '/'))) {
                    MysticalAugmentsShape.publish(node, MysticalAugmentsCompatibility.TICK_PROOF, MysticalAugmentsShape.armorTick(node));
                }
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return true;
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
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
