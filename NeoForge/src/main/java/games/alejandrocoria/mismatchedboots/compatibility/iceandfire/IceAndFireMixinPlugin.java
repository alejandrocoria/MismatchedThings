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
            "games.alejandrocoria.mismatchedboots.mixin.IceAndFireDragonDamageMixin";
    private Boolean compatible;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !OPTIONAL_MIXIN.equals(mixinClassName) || isCompatible();
    }

    private synchronized boolean isCompatible() {
        if (compatible != null) {
            return compatible;
        }
        boolean handlerPresent = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode handler = provider.getClassNode(IceAndFireDragonDamageShape.HANDLER.replace('/', '.'));
            handlerPresent = true;
            ClassNode scale = provider.getClassNode(IceAndFireDragonDamageShape.SCALE.replace('/', '.'));
            ClassNode steel = provider.getClassNode(IceAndFireDragonDamageShape.STEEL.replace('/', '.'));
            compatible = IceAndFireDragonDamageShape.isCompatible(handler, scale, steel);
            if (!compatible) {
                Constants.LOG.warn("Ice and Fire dragon-damage handler changed; boot compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            compatible = false;
            if (handlerPresent) {
                Constants.LOG.warn("Ice and Fire armor API is incomplete; boot compatibility disabled");
            }
        } catch (Exception | LinkageError exception) {
            compatible = false;
            Constants.LOG.warn("Could not inspect Ice and Fire dragon-damage handler; boot compatibility disabled",
                    exception);
        }
        return compatible;
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
