package games.alejandrocoria.mismatchedboots.compatibility.betterx;

import games.alejandrocoria.mismatchedboots.Constants;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
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
    private static final String BETTER_NETHER_MOD_ID = "betternether";
    private static final String BETTER_NETHER_MIXIN =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.betterx."
                    + "BetterNetherPiglinArmorMixin";
    private static final String CONFIGS = "org.betterx.betternether.config.Configs";
    private static final String DEFAULT_CONFIG =
            "org.betterx.betternether.config.DefaultWorldConfig";
    private static final String EQUIPMENT_ITEMS =
            "org.betterx.betternether.registry.item.NetherEquipmentItems";
    private static final String NETHER_SET =
            "org.betterx.betternether.items.complex.NetherSet";
    private static final String EQUIPMENT_SET =
            "de.ambertation.wover.complex.api.equipment.EquipmentSet";
    private static final String UPSTREAM_MIXIN =
            "org.betterx.betternether.mixin.common.piglin.PiglinAiMixin";
    private static final String DEFAULT_CONFIG_DESCRIPTOR =
            "Lorg/betterx/betternether/config/DefaultWorldConfig;";
    private static final String BOOLEAN_VALUE_DESCRIPTOR =
            "Lde/ambertation/wunderlib/configs/AbstractConfig$BooleanValue;";
    private static final String NETHER_SET_DESCRIPTOR =
            "Lorg/betterx/betternether/items/complex/NetherSet;";
    private static final String EQUIPMENT_SET_INTERNAL =
            "de/ambertation/wover/complex/api/equipment/EquipmentSet";
    private static final String ARMOR_PIECES_DESCRIPTOR =
            "()[Lnet/minecraft/world/item/Item;";
    private static final String UPSTREAM_HANDLER_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

    private Boolean betterEndCompatible;
    private Boolean betterNetherCompatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (BETTER_END_MIXIN.equals(mixinClassName)) {
            return isBetterEndCompatible();
        }
        if (BETTER_NETHER_MIXIN.equals(mixinClassName)) {
            return isBetterNetherCompatible();
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

    static boolean hasCompatibleBetterNetherApi(
            ClassNode configs,
            ClassNode defaultConfig,
            ClassNode equipmentItems,
            ClassNode netherSet,
            ClassNode equipmentSet,
            ClassNode upstreamMixin) {
        return isPublicClass(configs)
                && hasField(configs, "GAME_RULES", DEFAULT_CONFIG_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && isPublicClass(defaultConfig)
                && hasField(defaultConfig, "piglinIgnoreNetherArmor", BOOLEAN_VALUE_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
                && isPublicClass(equipmentItems)
                && hasField(equipmentItems, "CINCINNASITE_SET", NETHER_SET_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && hasField(equipmentItems, "NETHER_RUBY_SET", NETHER_SET_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && hasField(equipmentItems, "FLAMING_RUBY_SET", NETHER_SET_DESCRIPTOR,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                && isPublicClass(netherSet)
                && EQUIPMENT_SET_INTERNAL.equals(netherSet.superName)
                && isPublicClass(equipmentSet)
                && hasMethod(equipmentSet, "getArmorPieces", ARMOR_PIECES_DESCRIPTOR,
                Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC)
                && hasUpstreamHeadInjection(upstreamMixin);
    }

    private static boolean isPublicInterface(ClassNode node) {
        return node != null
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static boolean isPublicClass(ClassNode node) {
        return node != null
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    private static boolean hasField(ClassNode owner, String name, String descriptor, int flags) {
        for (FieldNode field : owner.fields) {
            if (name.equals(field.name)
                    && descriptor.equals(field.desc)
                    && (field.access & flags) == flags) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(
            ClassNode owner, String name, String descriptor, int requiredFlags, int forbiddenFlags) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name)
                    && descriptor.equals(method.desc)
                    && (method.access & requiredFlags) == requiredFlags
                    && (method.access & forbiddenFlags) == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUpstreamHeadInjection(ClassNode upstreamMixin) {
        if (!isPublicClass(upstreamMixin)) {
            return false;
        }
        for (MethodNode method : upstreamMixin.methods) {
            if (!"bn_isWearingSafeArmor".equals(method.name)
                    || !UPSTREAM_HANDLER_DESCRIPTOR.equals(method.desc)
                    || method.visibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if ("Lorg/spongepowered/asm/mixin/injection/Inject;".equals(annotation.desc)
                        && annotationValueContains(annotation, "method", "isWearingSafeArmor")
                        && Boolean.TRUE.equals(annotationValue(annotation, "cancellable"))
                        && hasHeadAt(annotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasHeadAt(AnnotationNode inject) {
        Object atValue = annotationValue(inject, "at");
        if (!(atValue instanceof List<?> annotations)) {
            return false;
        }
        for (Object value : annotations) {
            if (value instanceof AnnotationNode at
                    && "Lorg/spongepowered/asm/mixin/injection/At;".equals(at.desc)
                    && "HEAD".equals(annotationValue(at, "value"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean annotationValueContains(
            AnnotationNode annotation, String key, String expected) {
        Object value = annotationValue(annotation, key);
        return value instanceof List<?> values && values.contains(expected);
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
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

    private synchronized boolean isBetterNetherCompatible() {
        if (betterNetherCompatible != null) {
            return betterNetherCompatible;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            betterNetherCompatible = hasCompatibleBetterNetherApi(
                    provider.getClassNode(CONFIGS),
                    provider.getClassNode(DEFAULT_CONFIG),
                    provider.getClassNode(EQUIPMENT_ITEMS),
                    provider.getClassNode(NETHER_SET),
                    provider.getClassNode(EQUIPMENT_SET),
                    provider.getClassNode(UPSTREAM_MIXIN));
            if (!betterNetherCompatible && isBetterNetherLoaded()) {
                Constants.LOG.warn(
                        "Better Nether is present with an incompatible API; compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            betterNetherCompatible = false;
            if (isBetterNetherLoaded()) {
                Constants.LOG.warn(
                        "Better Nether is present without its expected API; compatibility disabled");
            }
        } catch (Exception | LinkageError exception) {
            betterNetherCompatible = false;
            if (isBetterNetherLoaded()) {
                Constants.LOG.warn(
                        "Could not inspect Better Nether API; compatibility disabled", exception);
            }
        }
        return betterNetherCompatible;
    }

    private static boolean isBetterEndLoaded() {
        return FabricLoader.getInstance().isModLoaded(BETTER_END_MOD_ID);
    }

    private static boolean isBetterNetherLoaded() {
        return FabricLoader.getInstance().isModLoaded(BETTER_NETHER_MOD_ID);
    }
}
