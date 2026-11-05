package games.alejandrocoria.mismatchedleggings.compatibility.createfly;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class CreateFlyMixinPlugin implements IMixinConfigPlugin {
    private static final String CARDBOARD_MIXIN =
            "games.alejandrocoria.mismatchedleggings.mixin.compatibility.createfly.CreateFlyCardboardArmorHandlerMixin";
    private static final String HANDLER = "com.zurrtum.create.content.equipment.armor.CardboardArmorHandler";
    private static final String ARMOR_ITEM = "com.zurrtum.create.content.equipment.armor.CardboardArmorItem";
    private static final String ALL_ITEMS = "com.zurrtum.create.AllItems";
    private static final String ARMOR_INTERNAL = ARMOR_ITEM.replace('.', '/');
    private Boolean cardboardCompatible;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !CARDBOARD_MIXIN.equals(mixinClassName) || isCardboardCompatible();
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

    static boolean hasCompatibleCardboardApi(ClassNode handler, ClassNode armorItem, ClassNode allItems) {
        if (!isPublicClass(handler) || !isPublicClass(armorItem) || !isPublicClass(allItems)) {
            return false;
        }

        boolean registeredItem = false;
        int fieldFlags = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        for (FieldNode field : allItems.fields) {
            if ("CARDBOARD_LEGGINGS".equals(field.name)
                    && ("L" + ARMOR_INTERNAL + ";").equals(field.desc)
                    && (field.access & fieldFlags) == fieldFlags) {
                registeredItem = true;
                break;
            }
        }
        if (!registeredItem) {
            return false;
        }

        for (MethodNode method : handler.methods) {
            int methodFlags = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            if (!"isCardboardArmor".equals(method.name)
                    || !"(Lnet/minecraft/world/item/ItemStack;)Z".equals(method.desc)
                    || (method.access & methodFlags) != methodFlags) {
                continue;
            }
            // A local semantic anchor, not a fingerprint of returns or unrelated method code.
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.INSTANCEOF && ARMOR_INTERNAL.equals(type.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublicClass(ClassNode node) {
        return node != null && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    private synchronized boolean isCardboardCompatible() {
        if (cardboardCompatible != null) {
            return cardboardCompatible;
        }
        cardboardCompatible = false;
        if (!FabricLoader.getInstance().isModLoaded("create")) {
            return false;
        }

        try {
            var provider = MixinService.getService().getBytecodeProvider();
            cardboardCompatible = hasCompatibleCardboardApi(provider.getClassNode(HANDLER),
                    provider.getClassNode(ARMOR_ITEM), provider.getClassNode(ALL_ITEMS));
            if (!cardboardCompatible) {
                LeggingsContent.LOG.warn("Create Fly Cardboard API is incompatible; leggings recognition disabled");
            }
        } catch (Exception | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not inspect Create Fly Cardboard API; leggings recognition disabled", failure);
        }
        return cardboardCompatible;
    }
}
