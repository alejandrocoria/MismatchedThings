package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class CreateFlyCharacterizationMixinPlugin implements IMixinConfigPlugin {
    private static final String TOOLTIP_MIXIN =
            "games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin."
                    + "MixedBootsTooltipFormatterMixin";
    private static final String TOOLTIP_MODIFIER_CLASS =
            "com.zurrtum.create.client.foundation.item.TooltipModifier";
    private static final String SIMPLE_REGISTRY_CLASS =
            "com.zurrtum.create.api.registry.SimpleRegistry";
    private static final String TOOLTIP_MODIFIER_DESCRIPTOR =
            "Lcom/zurrtum/create/client/foundation/item/TooltipModifier;";
    private static final String SIMPLE_REGISTRY_DESCRIPTOR =
            "Lcom/zurrtum/create/api/registry/SimpleRegistry;";
    private static final String MODIFY_TOOLTIP =
            "(Ljava/util/List;Lnet/minecraft/world/entity/player/Player;)V";
    private static final String GET_REGISTRY_VALUE =
            "(Ljava/lang/Object;)Ljava/lang/Object;";

    private Boolean tooltipCompatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (TOOLTIP_MIXIN.equals(mixinClassName)) {
            return isTooltipCompatible();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    static boolean hasCompatibleTooltipApi(
            ClassNode tooltipModifier, ClassNode simpleRegistry) {
        return isPublicInterface(tooltipModifier)
                && isPublicInterface(simpleRegistry)
                && hasField(
                        tooltipModifier,
                        "REGISTRY",
                        SIMPLE_REGISTRY_DESCRIPTOR,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && hasMethod(
                        tooltipModifier,
                        "modify",
                        MODIFY_TOOLTIP,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)
                && hasMethod(
                        simpleRegistry,
                        "get",
                        GET_REGISTRY_VALUE,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
    }

    private static boolean isPublicInterface(ClassNode node) {
        return node != null
                && (node.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE))
                == (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE);
    }

    private static boolean hasField(
            ClassNode owner, String name, String descriptor, int requiredFlags) {
        for (FieldNode field : owner.fields) {
            if (name.equals(field.name)
                    && descriptor.equals(field.desc)
                    && (field.access & requiredFlags) == requiredFlags) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(
            ClassNode owner, String name, String descriptor, int requiredFlags) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name)
                    && descriptor.equals(method.desc)
                    && (method.access & requiredFlags) == requiredFlags) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean isTooltipCompatible() {
        if (tooltipCompatible != null) {
            return tooltipCompatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            tooltipCompatible = hasCompatibleTooltipApi(
                    provider.getClassNode(TOOLTIP_MODIFIER_CLASS),
                    provider.getClassNode(SIMPLE_REGISTRY_CLASS));
        } catch (Exception | LinkageError exception) {
            tooltipCompatible = false;
        }
        return tooltipCompatible;
    }
}
