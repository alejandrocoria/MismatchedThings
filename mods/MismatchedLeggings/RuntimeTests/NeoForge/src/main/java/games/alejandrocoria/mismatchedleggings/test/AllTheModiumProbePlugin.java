package games.alejandrocoria.mismatchedleggings.test;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** The separate broken process removes the audited declaration, not production guard state. */
public final class AllTheModiumProbePlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return Boolean.getBoolean("leggings.test.allTheModium");
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (Boolean.getBoolean("leggings.test.allTheModiumBroken")) {
            boolean removed = targetClass.methods.removeIf(method -> method.name.equals("inventoryTick")
                    && method.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V"));
            if (!removed) {
                throw new IllegalStateException("Native tick declaration was not removed for the incompatible API control");
            }
        }
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
}
