package games.alejandrocoria.mismatchedboots.compatibility.betterx;

import games.alejandrocoria.mismatchedboots.Constants;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class BetterXMixinPlugin implements IMixinConfigPlugin {
    private static final String BETTER_END_MOD_ID = "betterend";
    private static final String BETTER_END_MIXIN =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.betterx."
                    + "BetterEndMobEffectApplierMixin";
    private static final String EFFECT_APPLIER_CLASS =
            "org.betterx.betterend.interfaces.MobEffectApplier";
    private static final String EFFECT_APPLIER_INTERNAL =
            "org/betterx/betterend/interfaces/MobEffectApplier";
    private static final String CRYSTALITE_BOOTS_CLASS =
            "org.betterx.betterend.item.CrystaliteBoots";
    private static final String APPLY_EFFECT = "(Lnet/minecraft/world/entity/LivingEntity;)V";

    private Boolean betterEndCompatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !BETTER_END_MIXIN.equals(mixinClassName) || isBetterEndCompatible();
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

    static boolean hasCompatibleBetterEndApi(ClassNode effectApplier, ClassNode crystaliteBoots) {
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

    private synchronized boolean isBetterEndCompatible() {
        if (betterEndCompatible != null) {
            return betterEndCompatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            betterEndCompatible = hasCompatibleBetterEndApi(
                    provider.getClassNode(EFFECT_APPLIER_CLASS),
                    provider.getClassNode(CRYSTALITE_BOOTS_CLASS));
            if (!betterEndCompatible && isBetterEndLoaded()) {
                Constants.LOG.warn("Better End is present with an incompatible API; compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            betterEndCompatible = false;
            if (isBetterEndLoaded()) {
                Constants.LOG.warn("Better End is present without its expected API; compatibility disabled");
            }
        } catch (Exception | LinkageError exception) {
            betterEndCompatible = false;
            if (isBetterEndLoaded()) {
                Constants.LOG.warn("Could not inspect Better End API; compatibility disabled", exception);
            }
        }
        return betterEndCompatible;
    }

    private static boolean isBetterEndLoaded() {
        return FabricLoader.getInstance().isModLoaded(BETTER_END_MOD_ID);
    }
}
