package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class CreateFlyCharacterizationMixinPlugin implements IMixinConfigPlugin {
    private static final String CARDBOARD_MIXIN =
            "games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin."
                    + "CardboardArmorHandlerMixin";
    private static final String DIVING_MIXIN =
            "games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin."
                    + "DivingBootsItemMixin";
    private static final String TOOLTIP_MIXIN =
            "games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin."
                    + "MixedBootsTooltipFormatterMixin";
    private static final String HANDLER_CLASS =
            "com.zurrtum.create.content.equipment.armor.CardboardArmorHandler";
    private static final String ARMOR_ITEM_CLASS =
            "com.zurrtum.create.content.equipment.armor.CardboardArmorItem";
    private static final String ALL_ITEMS_CLASS = "com.zurrtum.create.AllItems";
    private static final String DIVING_BOOTS_CLASS =
            "com.zurrtum.create.content.equipment.armor.DivingBootsItem";
    private static final String TOOLTIP_MODIFIER_CLASS =
            "com.zurrtum.create.client.foundation.item.TooltipModifier";
    private static final String SIMPLE_REGISTRY_CLASS =
            "com.zurrtum.create.api.registry.SimpleRegistry";
    private static final String ARMOR_ITEM_INTERNAL = ARMOR_ITEM_CLASS.replace('.', '/');
    private static final String DIVING_BOOTS_INTERNAL = DIVING_BOOTS_CLASS.replace('.', '/');
    private static final String STACK_PREDICATE = "(Lnet/minecraft/world/item/ItemStack;)Z";
    private static final String ARMOR_ITEM_DESCRIPTOR = "L" + ARMOR_ITEM_INTERNAL + ";";
    private static final String DIVING_BOOTS_DESCRIPTOR = "L" + DIVING_BOOTS_INTERNAL + ";";
    private static final String ENTITY_TO_STACK =
            "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/item/ItemStack;";
    private static final String ENTITY_TO_BOOLEAN = "(Lnet/minecraft/world/entity/Entity;)Z";
    private static final String ENTITY_TO_VOID = "(Lnet/minecraft/world/entity/Entity;)V";
    private static final String PLAYER_BOOLEAN_TO_VOID =
            "(Lnet/minecraft/world/entity/player/Player;Z)V";
    private static final String TOOLTIP_MODIFIER_DESCRIPTOR =
            "Lcom/zurrtum/create/client/foundation/item/TooltipModifier;";
    private static final String SIMPLE_REGISTRY_DESCRIPTOR =
            "Lcom/zurrtum/create/api/registry/SimpleRegistry;";
    private static final String MODIFY_TOOLTIP =
            "(Ljava/util/List;Lnet/minecraft/world/entity/player/Player;)V";
    private static final String GET_REGISTRY_VALUE =
            "(Ljava/lang/Object;)Ljava/lang/Object;";

    private Boolean cardboardCompatible;
    private Boolean divingCompatible;
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
        if (CARDBOARD_MIXIN.equals(mixinClassName)) {
            return isCardboardCompatible();
        }
        if (DIVING_MIXIN.equals(mixinClassName)) {
            return isDivingCompatible();
        }
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

    static boolean hasCompatibleCardboardApi(
            ClassNode handler, ClassNode armorItem, ClassNode allItems) {
        return isPublicClass(handler)
                && isPublicClass(armorItem)
                && isPublicClass(allItems)
                && hasCardboardPredicate(handler)
                && hasField(
                        allItems,
                        "CARDBOARD_BOOTS",
                        ARMOR_ITEM_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
    }

    static boolean hasCompatibleDivingApi(ClassNode divingBoots, ClassNode allItems) {
        return isPublicClass(divingBoots)
                && isPublicClass(allItems)
                && hasDivingWornItemPredicate(divingBoots)
                && hasMethod(
                        divingBoots,
                        "isWornBy",
                        ENTITY_TO_BOOLEAN,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && hasMethod(
                        divingBoots,
                        "accelerateDescentUnderwater",
                        ENTITY_TO_VOID,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && hasMethod(
                        divingBoots,
                        "onLavaTravel",
                        PLAYER_BOOLEAN_TO_VOID,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && hasField(
                        allItems,
                        "COPPER_DIVING_BOOTS",
                        DIVING_BOOTS_DESCRIPTOR,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && hasField(
                        allItems,
                        "NETHERITE_DIVING_BOOTS",
                        DIVING_BOOTS_DESCRIPTOR,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
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

    private static boolean hasCardboardPredicate(ClassNode handler) {
        for (MethodNode method : handler.methods) {
            if (!"isCardboardArmor".equals(method.name)
                    || !STACK_PREDICATE.equals(method.desc)
                    || (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                    != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode typeInstruction
                        && typeInstruction.getOpcode() == Opcodes.INSTANCEOF
                        && ARMOR_ITEM_INTERNAL.equals(typeInstruction.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasDivingWornItemPredicate(ClassNode divingBoots) {
        for (MethodNode method : divingBoots.methods) {
            if (!"getWornItem".equals(method.name)
                    || !ENTITY_TO_STACK.equals(method.desc)
                    || (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                    != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode typeInstruction
                        && typeInstruction.getOpcode() == Opcodes.INSTANCEOF
                        && DIVING_BOOTS_INTERNAL.equals(typeInstruction.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublicClass(ClassNode node) {
        return node != null
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
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

    private synchronized boolean isCardboardCompatible() {
        if (cardboardCompatible != null) {
            return cardboardCompatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            cardboardCompatible = hasCompatibleCardboardApi(
                    provider.getClassNode(HANDLER_CLASS),
                    provider.getClassNode(ARMOR_ITEM_CLASS),
                    provider.getClassNode(ALL_ITEMS_CLASS));
        } catch (Exception | LinkageError exception) {
            cardboardCompatible = false;
        }
        return cardboardCompatible;
    }

    private synchronized boolean isDivingCompatible() {
        if (divingCompatible != null) {
            return divingCompatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            divingCompatible = hasCompatibleDivingApi(
                    provider.getClassNode(DIVING_BOOTS_CLASS),
                    provider.getClassNode(ALL_ITEMS_CLASS));
        } catch (Exception | LinkageError exception) {
            divingCompatible = false;
        }
        return divingCompatible;
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
