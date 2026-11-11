package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnetApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect;
import net.silentchaos512.gear.util.TraitHelper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained binding controls: native item ticks are the oracle, never the research candidate. */
public final class MagnetChecks {
    private MagnetChecks() {}

    static void run(ServerPlayer player, StringBuilder report, boolean absent) throws Throwable {
        String variant = System.getProperty("leggings.test.sgMagnetVariant", "");
        if (variant.isEmpty()) {
            return;
        }
        if (absent) {
            require(!SilentGearMagnet.available(), "Absent SG acquired magnet readiness");
            report.append("Magnet A: quiet absence, no native binding\n");
            return;
        }

        require(!MagnetTestPlugin.actualOrder.isEmpty(), "Missing actual magnet transformation order");
        ItemStack magnetic = magnetic(FoundationChecks.armor("iron"), player);
        ItemStack greedy = FoundationChecks.armor("blaze_gold");
        TraitInstance magnetTrait = trait(magnetic, "magnetic");
        TraitInstance greedyTrait = trait(greedy, "greedy");
        require(magnetTrait.getLevel() == 5 && greedyTrait.getLevel() == 3, "Native leggings acquisition levels differ");

        Vec3 nativePush = measure(player, () -> magnetic.copy().inventoryTick(player.level(), player, EquipmentSlot.LEGS));
        require(nativePush.lengthSqr() > 0, "Independent native magnet control is inert");
        var roots = FoundationChecks.paid(player, magnetic, greedy);
        boolean refused = variant.equals("callback") || variant.equals("proof");
        for (var root : roots) {
            ItemStack before = root.copy();
            var push = measure(player, () -> root.inventoryTick(player.level(), player, EquipmentSlot.LEGS));
            require(refused ? push.equals(Vec3.ZERO) : push.distanceToSqr(nativePush) < 1E-20,
                    "Ordinary mixed dispatch disagrees with final readiness/native movement");
            require(ItemStack.matches(before, root), "Mixed magnet tick changed root/history");
        }
        require(SilentGearMagnet.available() != refused, "Final magnet readiness disagrees with controlled mutation");
        if (refused) {
            expectRejected(() -> new SilentGearMagnetApi().requireProof());
            require(!SilentGearMagnet.available(), "Repeated refusal rearmed the magnet feature");
        } else {
            var api = new SilentGearMagnetApi();
            api.requireProof();
            for (var root : roots) {
                ItemStack before = root.copy();
                ItemStack carrier = api.carrier(root);
                ItemStack carrierBefore = carrier.copy();
                for (var nativeSource : List.of(magnetic, greedy)) {
                    var instance = nativeSource == magnetic ? magnetTrait : greedyTrait;
                    var effect = api.admittedEffect(instance);
                    Vec3 expected = measure(player, () -> nativeSource.copy().inventoryTick(player.level(), player, EquipmentSlot.LEGS));
                    require(expected.lengthSqr() > 0, "Native trait-specific magnet control is inert");
                    Vec3 actual = measure(player, () -> api.tick(effect, player, instance, carrier, true));
                    require(actual.distanceToSqr(expected) < 1E-20, "Guarded direct binding differs from native movement");
                    require(measure(player, () -> api.tick(effect, player, instance, carrier, false)).equals(Vec3.ZERO),
                            "Unselected direct binding queued work");
                }
                require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier),
                        "Guarded native binding changed root/carrier/history");
            }
            definitions(api, magnetTrait, greedyTrait, player, roots.getFirst());
        }
        if (Boolean.getBoolean("leggings.test.boots")) {
            sibling(player, nativePush);
        }
        report.append("Magnet A/B: native Magnetic V recipe/Greedy III construction; guarded binding ")
                .append(refused ? "refused locally; native controls/earlier SG retained" : "matches native movement/current definitions")
                .append("; ordinary mixed dispatch follows readiness; actual order=").append(MagnetTestPlugin.actualOrder).append('\n');
        if (!refused) { MagnetDispatchChecks.run(player, magnetic, greedy, report); }
    }

    private static void definitions(SilentGearMagnetApi api, TraitInstance magnetic, TraitInstance greedy,
                                    ServerPlayer player, ItemStack root) throws Throwable {
        var definition = magnetic.getTrait();
        Field effects = field(definition.getClass(), "effects");
        Object original = effects.get(definition);
        Object magnet = api.admittedEffect(magnetic);
        api.admittedEffect(greedy); // Its native mining-speed companion does not invalidate the selected magnet.
        try {
            for (var unsupported : List.of(List.of(), List.of(magnet, magnet), List.of(new ForeignMagnet()))) {
                effects.set(definition, unsupported);
                expectRejected(() -> api.admittedEffect(magnetic));
            }
            effects.set(definition, original);
            Field strength = field(magnet.getClass(), "pullStrength");
            float prior = strength.getFloat(magnet);
            try {
                strength.setFloat(magnet, Float.NaN);
                expectRejected(() -> api.admittedEffect(magnetic));
                strength.setFloat(magnet, 0.08F);
                require(api.admittedEffect(magnetic) == magnet, "Finite native balance change was frozen out");
            } finally {
                strength.setFloat(magnet, prior);
            }
            effects.set(definition, List.of(ItemMagnetTraitEffect.attractAll()));
            ItemStack carrier = api.carrier(root);
            require(measure(player, () -> expectRejected(() -> api.tick(magnet, player, magnetic, carrier, true))).equals(Vec3.ZERO),
                    "Stale native effect queued work before rejection");
        } finally {
            effects.set(definition, original);
        }
        require(api.admittedEffect(magnetic) == magnet, "Restored current definition was not reread");
    }

    private static void sibling(ServerPlayer player, Vec3 nativePush) throws Throwable {
        var type = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet");
        require((boolean) type.getMethod("available").invoke(null), "Leggings proof disabled current Boots magnets");
        var gear = (net.silentchaos512.gear.api.item.GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"));
        ItemStack source = gear.construct(List.of(net.silentchaos512.gear.gear.part.PartInstance.from(FoundationChecks.part("boot_plates", "iron"))));
        source = magnetic(source, player);
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        ItemStack boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        ItemStack before = boots.copy();
        require(measure(player, () -> boots.inventoryTick(player.level(), player, EquipmentSlot.FEET)).distanceToSqr(nativePush) < 1E-20,
                "Current packaged Boots did not preserve its native magnet dispatch");
        require(ItemStack.matches(before, boots), "Leggings binding changed current Boots data");
    }

    static ItemStack magnetic(ItemStack source, ServerPlayer player) throws Exception {
        var recipeClass = Class.forName("net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe");
        Object recipe = recipeClass.getField("INSTANCE").get(null);
        var upgrade = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:magnetic_upgrade")));
        var input = CraftingInput.of(2, 1, List.of(source, upgrade));
        require((boolean) recipeClass.getMethod("matches", CraftingInput.class, Level.class).invoke(recipe, input, player.level()),
                "Native Magnetic Upgrade recipe rejected armor");
        ItemStack before = source.copy();
        var result = (ItemStack) recipeClass.getMethod("assemble", CraftingInput.class).invoke(recipe, input);
        require(ItemStack.matches(before, source), "Native upgrade recipe changed its input");
        return result;
    }

    static TraitInstance trait(ItemStack source, String id) {
        return TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().toString().equals("silentgear:" + id)).findFirst().orElseThrow();
    }

    private static Vec3 measure(ServerPlayer player, Action action) throws Throwable {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        var item = new ItemEntity(player.level(), player.getX() + 2, player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        player.level().getChunk(item.blockPosition());
        item.setNoGravity(true);
        item.setNoPickUpDelay();
        item.setDeltaMovement(Vec3.ZERO);
        require(player.level().addFreshEntity(item), "Could not spawn native magnet control");
        try {
            require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16)).contains(item),
                    "Native control item is not query-visible at " + item.position() + "; player=" + player.position());
            action.run();
            require(item.getDeltaMovement().equals(Vec3.ZERO), "Binding bypassed native level-post publication");
            NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
            return item.getDeltaMovement();
        } finally {
            item.discard();
            NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        }
    }

    private static void expectRejected(Action action) throws Throwable {
        try {
            action.run();
        } catch (ReflectiveOperationException | IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Unsupported native magnet binding was admitted");
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static void unknownNativeCall() {}

    private static final class ForeignMagnet extends ItemMagnetTraitEffect {
        private ForeignMagnet() {
            super(0.06F, 3, Optional.empty(), "");
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Throwable;
    }
}
