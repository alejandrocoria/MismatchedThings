package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.HashSet;
import java.util.Set;

/** Closed call/write surfaces for C's selected callbacks, complemented by exact runtime scalar deltas.
 * Debug nodes and harmless NOPs do not affect admission; no whole-class bytecode equality is required. */
final class SilentGearLiveShape implements Opcodes {
    private static final String SG = "net/silentchaos512/gear/";
    private static final String MC = "net/minecraft/";
    static final String OXIDATION = SG + "gear/trait/effect/OxidationTraitEffect";
    static final String NUMBER = SG + "gear/trait/effect/NumberPropertyModifierTraitEffect";
    static final String MATH = NUMBER + "$StatMod";
    private static final String CONTEXT = SG + "api/traits/TraitActionContext";
    private static final String COMPONENT = SG + "setup/SgDataComponents";
    private static final String STACK = MC + "world/item/ItemStack";
    private static final String TRAIT = SG + "gear/trait/Trait";

    private SilentGearLiveShape() {}

    static boolean target(String name) { return name.equals(OXIDATION) || name.equals(NUMBER) || name.equals(MATH); }

    static boolean ready(ClassNode node) {
        if (node.name.equals(OXIDATION)) {
            return closed(node, "inventoryTick", Set.of(MC + "world/level/Level#isClientSide", OXIDATION + "#canOxidize",
                    CONTEXT + "#gear", CONTEXT + "#trait", CONTEXT + "#traitLevel", TRAIT + "#getMaxLevel",
                    STACK + "#getOrDefault", STACK + "#set", "java/lang/Byte#valueOf", "java/lang/Byte#byteValue",
                    "java/lang/Integer#valueOf", "java/lang/Integer#intValue", OXIDATION + "#getStageTime", "java/util/Optional#isEmpty"),
                    Set.of(MC + "world/entity/Entity#tickCount", OXIDATION + "#nextOxidationTrait", COMPONENT + "#OXIDATION_STAGE",
                            COMPONENT + "#OXIDATION_COUNTER", COMPONENT + "#RECALCULATE_FLAG", MC + "util/Unit#INSTANCE"))
                    && closed(node, "canOxidize", Set.of(CONTEXT + "#gear", "net/neoforged/neoforge/registries/DeferredHolder#get",
                    SG + "util/GearData#hasPartOfType"), Set.of(SG + "setup/gear/PartTypes#COATING"))
                    && closed(node, "getStageTime", Set.of("java/util/List#size", "java/util/List#get", MC + "util/Mth#clamp", "java/lang/Integer#intValue"),
                    Set.of(OXIDATION + "#counterTimesPerLevel"))
                    && closed(node, "transformTrait", Set.of(SG + "api/property/ComputeContext$Gear#gear", STACK + "#getOrDefault", STACK + "#set",
                    "java/lang/Byte#valueOf", "java/lang/Byte#byteValue", "java/util/Optional#empty", "java/util/Optional#isPresent",
                    "java/util/Optional#get", "java/util/Optional#of", TRAIT + "#getMaxLevel", SG + "api/traits/TraitInstance#of"),
                    Set.of(COMPONENT + "#OXIDATION_STAGE", OXIDATION + "#nextOxidationTrait"));
        }
        if (node.name.equals(NUMBER)) {
            return closed(node, "getBonusProperties", Set.of("java/util/Map#get", SG + "api/property/NumberPropertyValue#value",
                    "java/lang/Float#floatValue", MATH + "#getAddedValue", SG + "api/property/NumberPropertyValue#<init>",
                    "java/util/List#of", SG + "api/traits/TraitEffect#getBonusProperties"),
                    Set.of(NUMBER + "#mods", SG + "api/property/NumberProperty$Operation#ADD"));
        }
        if (node.name.equals(MATH)) {
            var method = method(node, "getAddedValue");
            if (method == null) { return false; }
            int multiplications = 0;
            for (var instruction : method.instructions) { if (instruction.getOpcode() == FMUL) { multiplications++; } }
            return multiplications == 3 && closed(node, "getAddedValue", Set.of(),
                    Set.of(MATH + "#multiplier", MATH + "#multiplyDamageRatio", MATH + "#multiplyOriginalValue"));
        }
        return false;
    }

    private static MethodNode method(ClassNode owner, String name) {
        var methods = owner.methods.stream().filter(method -> method.name.equals(name)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }

    private static boolean closed(ClassNode owner, String name, Set<String> expectedCalls, Set<String> expectedReads) {
        var method = method(owner, name);
        if (method == null || !method.tryCatchBlocks.isEmpty()) { return false; }
        var calls = new HashSet<String>();
        var reads = new HashSet<String>();
        for (var instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if (instruction instanceof InvokeDynamicInsnNode || opcode == ATHROW || opcode == MONITORENTER || opcode == MONITOREXIT
                    || opcode == PUTFIELD || opcode == PUTSTATIC) { return false; }
            if (instruction instanceof TypeInsnNode type && opcode == NEW
                    && !type.desc.equals(SG + "api/property/NumberPropertyValue")) { return false; }
            if (instruction instanceof MethodInsnNode call) { calls.add(call.owner + "#" + call.name); }
            if (instruction instanceof FieldInsnNode field) { reads.add(field.owner + "#" + field.name); }
        }
        return calls.equals(expectedCalls) && reads.equals(expectedReads);
    }
}
