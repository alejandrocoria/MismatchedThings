package games.alejandrocoria.mismatchedboots.compatibility.createfly;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class CreateFlyMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE =
            "games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly.";
    private static final String CARDBOARD_MIXIN =
            MIXIN_PACKAGE + "CreateFlyCardboardArmorHandlerMixin";
    private static final String BELT_MIXIN = MIXIN_PACKAGE + "CreateFlyBeltBlockMixin";

    private static final String HANDLER_CLASS =
            "com.zurrtum.create.content.equipment.armor.CardboardArmorHandler";
    private static final String ARMOR_ITEM_CLASS =
            "com.zurrtum.create.content.equipment.armor.CardboardArmorItem";
    private static final String ALL_ITEMS_CLASS = "com.zurrtum.create.AllItems";
    private static final String BELT_BLOCK_CLASS =
            "com.zurrtum.create.content.kinetics.belt.BeltBlock";

    private static final String ARMOR_ITEM_INTERNAL = ARMOR_ITEM_CLASS.replace('.', '/');
    private static final String ALL_ITEMS_INTERNAL = ALL_ITEMS_CLASS.replace('.', '/');
    private static final String ARMOR_ITEM_DESCRIPTOR = "L" + ARMOR_ITEM_INTERNAL + ";";
    private static final String STACK_PREDICATE = "(Lnet/minecraft/world/item/ItemStack;)Z";
    private static final String ENTITY_INSIDE =
            "(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V";
    private static final String EQUIPMENT_SLOT_INTERNAL =
            "net/minecraft/world/entity/EquipmentSlot";
    private static final String EQUIPMENT_SLOT_DESCRIPTOR =
            "Lnet/minecraft/world/entity/EquipmentSlot;";
    private static final String PLAYER_INTERNAL = "net/minecraft/world/entity/player/Player";
    private static final String GET_ITEM_BY_SLOT =
            "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
    private static final String ITEM_STACK_INTERNAL = "net/minecraft/world/item/ItemStack";
    private static final String ITEM_STACK_IS = "(Ljava/lang/Object;)Z";

    private Boolean cardboardCompatible;
    private Boolean beltCompatible;

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
        if (BELT_MIXIN.equals(mixinClassName)) {
            return isBeltCompatible();
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

    static boolean hasCompatibleBeltConsumer(ClassNode beltBlock) {
        if (!isPublicClass(beltBlock)) {
            return false;
        }

        MethodNode entityInside = null;
        for (MethodNode method : beltBlock.methods) {
            if ("entityInside".equals(method.name) && ENTITY_INSIDE.equals(method.desc)) {
                if (entityInside != null
                        || (method.access & Opcodes.ACC_PUBLIC) == 0
                        || (method.access & Opcodes.ACC_STATIC) != 0) {
                    return false;
                }
                entityInside = method;
            }
        }
        return entityInside != null && hasSingleCardboardBeltPredicate(entityInside);
    }

    private static boolean hasCardboardPredicate(ClassNode handler) {
        for (MethodNode method : handler.methods) {
            if (!"isCardboardArmor".equals(method.name)
                    || !STACK_PREDICATE.equals(method.desc)
                    || (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                    != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode typeInstruction
                        && typeInstruction.getOpcode() == Opcodes.INSTANCEOF
                        && ARMOR_ITEM_INTERNAL.equals(typeInstruction.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSingleCardboardBeltPredicate(MethodNode method) {
        int shiftCalls = 0;
        int feetFields = 0;
        int cardboardFields = 0;
        int stackPredicates = 0;
        int completePatterns = 0;
        JumpInsnNode shiftBranch = null;

        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && PLAYER_INTERNAL.equals(call.owner)
                    && "isShiftKeyDown".equals(call.name)
                    && "()Z".equals(call.desc)) {
                shiftCalls++;
                AbstractInsnNode next = nextCodeInstruction(call);
                if (next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ) {
                    shiftBranch = jump;
                }
            }
            if (!(instruction instanceof FieldInsnNode feet)
                    || feet.getOpcode() != Opcodes.GETSTATIC
                    || !EQUIPMENT_SLOT_INTERNAL.equals(feet.owner)
                    || !"FEET".equals(feet.name)
                    || !EQUIPMENT_SLOT_DESCRIPTOR.equals(feet.desc)) {
                continue;
            }

            feetFields++;
            AbstractInsnNode getItem = nextCodeInstruction(feet);
            AbstractInsnNode cardboard = nextCodeInstruction(getItem);
            AbstractInsnNode predicate = nextCodeInstruction(cardboard);
            AbstractInsnNode branch = nextCodeInstruction(predicate);
            AbstractInsnNode rejected = nextCodeInstruction(branch);

            if (getItem instanceof MethodInsnNode getItemCall
                    && getItemCall.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && PLAYER_INTERNAL.equals(getItemCall.owner)
                    && "getItemBySlot".equals(getItemCall.name)
                    && GET_ITEM_BY_SLOT.equals(getItemCall.desc)
                    && cardboard instanceof FieldInsnNode cardboardField
                    && cardboardField.getOpcode() == Opcodes.GETSTATIC
                    && ALL_ITEMS_INTERNAL.equals(cardboardField.owner)
                    && "CARDBOARD_BOOTS".equals(cardboardField.name)
                    && ARMOR_ITEM_DESCRIPTOR.equals(cardboardField.desc)
                    && predicate instanceof MethodInsnNode predicateCall
                    && predicateCall.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && ITEM_STACK_INTERNAL.equals(predicateCall.owner)
                    && "is".equals(predicateCall.name)
                    && ITEM_STACK_IS.equals(predicateCall.desc)
                    && branch instanceof JumpInsnNode itemBranch
                    && itemBranch.getOpcode() == Opcodes.IFNE
                    && rejected != null
                    && rejected.getOpcode() == Opcodes.RETURN
                    && shiftBranch != null
                    && shiftBranch.label == itemBranch.label) {
                completePatterns++;
            }
        }

        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && ALL_ITEMS_INTERNAL.equals(field.owner)
                    && "CARDBOARD_BOOTS".equals(field.name)
                    && ARMOR_ITEM_DESCRIPTOR.equals(field.desc)) {
                cardboardFields++;
            } else if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && ITEM_STACK_INTERNAL.equals(call.owner)
                    && "is".equals(call.name)
                    && ITEM_STACK_IS.equals(call.desc)) {
                stackPredicates++;
            }
        }

        return shiftCalls == 1
                && feetFields == 1
                && cardboardFields == 1
                && stackPredicates == 1
                && completePatterns == 1;
    }

    private static AbstractInsnNode nextCodeInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction == null ? null : instruction.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    private static boolean isPublicClass(ClassNode node) {
        return node != null
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
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

    private synchronized boolean isCardboardCompatible() {
        if (cardboardCompatible != null) {
            return cardboardCompatible;
        }

        cardboardCompatible = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode handler = provider.getClassNode(HANDLER_CLASS);
            cardboardCompatible = hasCompatibleCardboardApi(
                    handler,
                    provider.getClassNode(ARMOR_ITEM_CLASS),
                    provider.getClassNode(ALL_ITEMS_CLASS));
            if (!cardboardCompatible) {
                Constants.LOG.warn(
                        "Create Fly Cardboard API is incompatible; Cardboard compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            // Create Fly is optional. Its complete absence is expected and silent.
        } catch (Exception | LinkageError exception) {
            Constants.LOG.warn(
                    "Could not inspect Create Fly Cardboard API; Cardboard compatibility disabled",
                    exception);
        }
        return cardboardCompatible;
    }

    private synchronized boolean isBeltCompatible() {
        if (beltCompatible != null) {
            return beltCompatible;
        }

        beltCompatible = false;
        if (!isCardboardCompatible()) {
            return false;
        }
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            ClassNode beltBlock = provider.getClassNode(BELT_BLOCK_CLASS);
            beltCompatible = hasCompatibleBeltConsumer(beltBlock);
            if (!beltCompatible) {
                Constants.LOG.warn(
                        "Create Fly Belt behavior is incompatible; Cardboard Belt compatibility disabled");
            }
        } catch (ClassNotFoundException exception) {
            // A partial Create Fly installation is treated like an absent optional consumer.
        } catch (Exception | LinkageError exception) {
            Constants.LOG.warn(
                    "Could not inspect Create Fly Belt behavior; Cardboard Belt compatibility disabled",
                    exception);
        }
        return beltCompatible;
    }
}
