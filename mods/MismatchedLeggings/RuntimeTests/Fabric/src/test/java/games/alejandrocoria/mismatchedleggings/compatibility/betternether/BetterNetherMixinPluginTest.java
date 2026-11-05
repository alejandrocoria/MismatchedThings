package games.alejandrocoria.mismatchedleggings.compatibility.betternether;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterNetherMixinPluginTest {
    @Test
    void usedBoundaryAcceptsUnrelatedUpstreamMembers() {
        var api = compatibleApi();
        api[5].methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "unrelatedHandler", "()V", null, null));
        assertTrue(compatible(api));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_config", "config_type", "set_type", "inheritance", "static_query",
            "private_query", "noncancellable", "target", "not_head"})
    void rejectsChangesToTheUsedBoundary(String change) {
        var api = compatibleApi();
        var injection = api[5].methods.getFirst().visibleAnnotations.getFirst();
        switch (change) {
            case "missing_config" -> api[0].fields.clear();
            case "config_type" -> api[1].fields.getFirst().desc = "Z";
            case "set_type" -> api[2].fields.getFirst().desc = "Ljava/lang/Object;";
            case "inheritance" -> api[3].superName = "java/lang/Object";
            case "static_query" -> api[4].methods.getFirst().access |= Opcodes.ACC_STATIC;
            case "private_query" -> api[4].methods.getFirst().access = Opcodes.ACC_PRIVATE;
            case "noncancellable" -> injection.values.set(5, false);
            case "target" -> injection.values.set(1, List.of("otherMethod"));
            case "not_head" -> ((AnnotationNode) ((List<?>) injection.values.get(3)).getFirst()).values.set(1, "RETURN");
            default -> throw new AssertionError(change);
        }
        assertFalse(compatible(api));
    }

    @Test
    void missingNativeTypesDisableTheMixin() {
        for (int index = 0; index < 6; index++) {
            var api = compatibleApi();
            api[index] = null;
            assertFalse(compatible(api));
        }
    }

    @Test
    void existingPositiveResultDoesNotResolveOrConsultNativeApi() {
        var callback = new CallbackInfoReturnable<Boolean>("isWearingSafeArmor", true, true);
        BetterNetherPiglinArmorAdapter.augment(null, callback);
        assertTrue(callback.getReturnValue());
    }

    private static boolean compatible(ClassNode[] api) {
        return BetterNetherMixinPlugin.hasCompatibleBetterNetherApi(api[0], api[1], api[2], api[3], api[4], api[5]);
    }

    private static ClassNode[] compatibleApi() {
        var configs = type();
        configs.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "GAME_RULES", "Lorg/betterx/betternether/config/DefaultWorldConfig;", null, null));
        var defaults = type();
        defaults.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "piglinIgnoreNetherArmor", "Lde/ambertation/wunderlib/configs/AbstractConfig$BooleanValue;", null, null));
        var items = type();
        for (String field : List.of("CINCINNASITE_SET", "NETHER_RUBY_SET", "FLAMING_RUBY_SET")) {
            items.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    field, "Lorg/betterx/betternether/items/complex/NetherSet;", null, null));
        }
        var netherSet = type();
        netherSet.superName = "de/ambertation/wover/complex/api/equipment/EquipmentSet";
        var equipmentSet = type();
        equipmentSet.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "getArmorPieces", "()[Lnet/minecraft/world/item/Item;", null, null));
        var upstream = type();
        var handler = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bn_isWearingSafeArmor",
                "(Lnet/minecraft/world/entity/LivingEntity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", null, null);
        var at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "HEAD"));
        var inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of("method", List.of("isWearingSafeArmor"), "at", List.of(at), "cancellable", true));
        handler.visibleAnnotations = new ArrayList<>(List.of(inject));
        upstream.methods.add(handler);
        return new ClassNode[]{configs, defaults, items, netherSet, equipmentSet, upstream};
    }

    private static ClassNode type() {
        var type = new ClassNode();
        type.access = Opcodes.ACC_PUBLIC;
        return type;
    }
}
