package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

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

public final class MysticalAugmentsMixinPlugin implements IMixinConfigPlugin, IExtension {
    private MysticalRefundShape refundShape;
    private boolean refundWarned;
    @Override public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public List<String> getMixins() {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(MysticalAugmentsShape.UTILS);
        } catch (ClassNotFoundException absent) { return List.of(); }
        catch (Exception | LinkageError failure) {
            Constants.LOG.warn("Could not inspect Mystical Agriculture augment API", failure); return List.of();
        }
        var selected = new java.util.ArrayList<>(List.of("MysticalEquipmentMixin", "MysticalArmorTickProofMixin", "MysticalHealthCleanupMixin"));
        try {
            ClassNode stack = MixinService.getService().getBytecodeProvider().getClassNode("net.minecraft.world.item.ItemStack");
            // NeoForge retains vanilla's ServerPlayer delegator as well as the real LivingEntity body.
            refundShape = new MysticalRefundShape(stack.methods.stream().noneMatch(m -> m.name.equals("applyDamage")
                    && m.desc.equals("(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")));
            selected.add("MysticalRefundMixin");
        } catch (Exception | LinkageError failure) {
            Constants.LOG.warn("Could not inspect Mystical Agriculture refund boundary; augment effects retained", failure);
        }
        return selected;
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals("net/minecraft/world/item/ItemStack")) {
            if (refundShape != null && !refundShape.publish(node) && !refundWarned) {
                refundWarned = true;
                Constants.LOG.warn("Mystical Agriculture refund flow incompatible; ordinary breakage retained without augment refunds");
            }
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
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
