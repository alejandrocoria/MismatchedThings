package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class IceAndFireMixinPlugin implements IMixinConfigPlugin {
    private static final String OPTIONAL_MIXIN =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.iceandfire.IceAndFireDamageMixin";
    private static final String LIGHTNING_MIXIN =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.iceandfire.IceAndFireLightningSetMixin";
    private Boolean compatible;
    private Boolean lightningCompatible;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (OPTIONAL_MIXIN.equals(mixinClassName)) {
            return isCompatible(false);
        }
        return !LIGHTNING_MIXIN.equals(mixinClassName) || isCompatible(true);
    }

    private synchronized boolean isCompatible(boolean lightning) {
        Boolean cached = lightning ? lightningCompatible : compatible;
        if (cached != null) {
            return cached;
        }
        boolean result;
        String integration = lightning ? "lightning-set" : "projectile/dragon";
        boolean handlerPresent = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode handler = provider.getClassNode(IceAndFireDamageShape.HANDLER.replace('/', '.'));
            handlerPresent = true;
            if (lightning) {
                ClassNode items = provider.getClassNode(IceAndFireDamageShape.ITEMS.replace('/', '.'));
                result = IceAndFireDamageShape.isLightningCompatible(handler, items);
            } else {
                ClassNode scale = provider.getClassNode(IceAndFireDamageShape.SCALE.replace('/', '.'));
                ClassNode steel = provider.getClassNode(IceAndFireDamageShape.STEEL.replace('/', '.'));
                ClassNode troll = provider.getClassNode(IceAndFireDamageShape.TROLL.replace('/', '.'));
                result = IceAndFireDamageShape.isCompatible(handler, scale, steel, troll);
            }
            if (!result) {
                Constants.LOG.warn("Ice and Fire {} local contract/API is incompatible; its boot compatibility disabled", integration);
            }
        } catch (ClassNotFoundException exception) {
            result = false;
            if (handlerPresent) {
                Constants.LOG.warn("Ice and Fire {} API is incomplete; its boot compatibility disabled", integration);
            }
        } catch (Exception | LinkageError exception) {
            result = false;
            Constants.LOG.warn("Could not inspect Ice and Fire {} handler; its boot compatibility disabled",
                               integration, exception);
        }
        if (lightning) {
            lightningCompatible = result;
        } else {
            compatible = result;
        }
        return result;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
}
