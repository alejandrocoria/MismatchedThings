package games.alejandrocoria.mismatchedboots.runtimetest.betterend.fabric;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class BetterEndSpikeMixinPlugin implements IMixinConfigPlugin {
    private static final String OPTIONAL_MIXIN =
            "games.alejandrocoria.mismatchedboots.runtimetest.betterend.fabric.mixin."
                    + "BetterEndMobEffectApplierMixin";
    private static final String EFFECT_APPLIER_CLASS =
            "org.betterx.betterend.interfaces.MobEffectApplier";
    private static final String EFFECT_APPLIER_INTERNAL =
            "org/betterx/betterend/interfaces/MobEffectApplier";
    private static final String CRYSTALITE_BOOTS_CLASS =
            "org.betterx.betterend.item.CrystaliteBoots";
    private static final String APPLY_EFFECT = "(Lnet/minecraft/world/entity/LivingEntity;)V";

    private Boolean compatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !OPTIONAL_MIXIN.equals(mixinClassName) || isApiCompatible();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    static boolean hasCompatibleApi(ClassNode effectApplier, ClassNode crystaliteBoots) {
        return effectApplier != null
                && crystaliteBoots != null
                && isPublicInterface(effectApplier)
                && isPublicClass(crystaliteBoots)
                && crystaliteBoots.interfaces.contains(EFFECT_APPLIER_INTERNAL)
                && declaresPublicInstanceMethod(effectApplier, true)
                && declaresPublicInstanceMethod(crystaliteBoots, false);
    }

    private static boolean isPublicInterface(ClassNode node) {
        return (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static boolean isPublicClass(ClassNode node) {
        return (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    private static boolean declaresPublicInstanceMethod(ClassNode owner, boolean requireAbstract) {
        for (MethodNode method : owner.methods) {
            if ("applyEffect".equals(method.name)
                    && APPLY_EFFECT.equals(method.desc)
                    && (method.access & Opcodes.ACC_PUBLIC) != 0
                    && (method.access & Opcodes.ACC_STATIC) == 0
                    && (!requireAbstract || (method.access & Opcodes.ACC_ABSTRACT) != 0)) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean isApiCompatible() {
        if (compatible != null) {
            return compatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            compatible = hasCompatibleApi(
                    provider.getClassNode(EFFECT_APPLIER_CLASS),
                    provider.getClassNode(CRYSTALITE_BOOTS_CLASS));
        } catch (ClassNotFoundException exception) {
            compatible = false;
        } catch (Exception | LinkageError exception) {
            compatible = false;
        }
        return compatible;
    }
}
