package games.alejandrocoria.mismatchedboots.compatibility.advancednetherite;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class AdvancedNetheriteMixinPlugin implements IMixinConfigPlugin {
    private static final String OPTIONAL_MIXIN =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.AdvancedNetheriteHooksMixin";
    private static final String HOOKS_CLASS = "com.autovw.advancednetherite.api.impl.IAdvancedHooks";
    private static final String ARMOR_CLASS = "com.autovw.advancednetherite.common.item.AdvancedArmorItem";
    private static final String STACK_PREDICATE = "(Lnet/minecraft/world/item/ItemStack;)Z";

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

    static boolean hasCompatibleApi(ClassNode hooks, ClassNode armor) {
        return hooks != null
                && armor != null
                && (hooks.access & Opcodes.ACC_PUBLIC) != 0
                && (hooks.access & Opcodes.ACC_INTERFACE) != 0
                && (armor.access & Opcodes.ACC_PUBLIC) != 0
                && (armor.access & Opcodes.ACC_INTERFACE) == 0
                && declaresPublicInstanceMethod(hooks, "pacifyEndermen")
                && declaresPublicInstanceMethod(hooks, "pacifyPhantoms")
                && declaresPublicInstanceMethod(armor, "pacifiesEndermen")
                && declaresPublicInstanceMethod(armor, "pacifiesPhantoms");
    }

    private static boolean declaresPublicInstanceMethod(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name)
                    && STACK_PREDICATE.equals(method.desc)
                    && (method.access & Opcodes.ACC_PUBLIC) != 0
                    && (method.access & Opcodes.ACC_STATIC) == 0) {
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
            ClassNode hooks = provider.getClassNode(HOOKS_CLASS);
            ClassNode armor = provider.getClassNode(ARMOR_CLASS);
            compatible = hasCompatibleApi(hooks, armor);
            if (!compatible) {
                Constants.LOG.warn("Advanced Netherite is present with an incompatible API; compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            compatible = false;
        } catch (Exception | LinkageError exception) {
            compatible = false;
            Constants.LOG.warn("Could not inspect Advanced Netherite API; compatibility disabled", exception);
        }
        return compatible;
    }
}
