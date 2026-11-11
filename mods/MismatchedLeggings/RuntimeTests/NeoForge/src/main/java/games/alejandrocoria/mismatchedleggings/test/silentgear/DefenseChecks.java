package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefense;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefenseApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.util.TraitHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained native oracles and final-contract controls; no candidate or forced readiness. */
public final class DefenseChecks {
    private DefenseChecks() {}

    static void run(ServerPlayer player, StringBuilder report, boolean absent) throws Throwable {
        String variant = System.getProperty("leggings.test.sgDefenseVariant", "");
        if (variant.isEmpty()) {
            return;
        }
        if (absent) {
            require(!SilentGearDefense.incomingAvailable() && !SilentGearDefense.fallAvailable()
                    && !SilentGearDefense.retaliationAvailable(), "Absent SG acquired a defensive binding");
            report.append("Defense A: quiet absence, no defensive contracts\n");
            return;
        }

        require(!DefenseTestPlugin.actualOrder.isEmpty(), "Missing final defensive transformation witness");
        require(SilentGearDefense.incomingAvailable() != variant.equals("routing"), "Wrong independent incoming readiness");
        require(SilentGearDefense.fallAvailable() != variant.equals("fall-query"), "Wrong independent fall readiness");
        require(SilentGearDefense.retaliationAvailable() != variant.equals("retaliation-query"), "Wrong independent retaliation readiness");
        require(DefenseTestPlugin.mutations == (List.of("routing", "fall-query", "retaliation-query").contains(variant) ? 1 : 0),
                "Missing/unexpected defensive corruption");
        require(ready("gear.trait.effect.NegateDamageTraitEffect", "mismatchedleggings$sgNegateReady"), "Native negate leaf refused");

        for (var slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        var heat = FoundationChecks.armor("crimson_iron");
        require(trait(heat, "heat_resistant").getLevel() == 4, "Native Heat IV acquisition changed");
        var recipeClass = Class.forName("net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe");
        var recipe = recipeClass.getField("INSTANCE").get(null);
        var input = CraftingInput.of(2, 1, List.of(heat, FoundationChecks.part("lining", "slime")));
        require((boolean) recipeClass.getMethod("matches", CraftingInput.class, Level.class).invoke(recipe, input, player.level()),
                "Native slime lining recipe refused leggings");
        var lined = (ItemStack) recipeClass.getMethod("assemble", CraftingInput.class).invoke(recipe, input);
        var heatTrait = trait(lined, "heat_resistant");
        var bounceTrait = trait(lined, "bounce");
        require(heatTrait.getLevel() == 4 && bounceTrait.getLevel() == 1, "Computed native lined levels changed");
        player.setItemSlot(EquipmentSlot.LEGS, lined);
        hit(player, player.damageSources().lava(), 16.8F);
        hit(player, player.damageSources().flyIntoWall(), 16);
        player.setDeltaMovement(0, 0, 0);
        var nativeFall = new LivingFallEvent(player, 12, 1);
        NeoForge.EVENT_BUS.post(nativeFall);
        require(nativeFall.isCanceled(), "Native Bounce fall oracle is inert");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);

        var api = new SilentGearDefenseApi();
        definitions(api, heatTrait, bounceTrait);
        for (var root : FoundationChecks.paid(player, lined, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            var before = root.copy();
            var resolved = selected(api, root);
            require(resolved.stream().filter(t -> t.getTraitId().toString().equals("silentgear:heat_resistant")).count() == 1
                    && resolved.stream().filter(t -> t.getTraitId().toString().equals("silentgear:bounce")).count() == 1,
                    "Actual source admission lost/duplicated selected traits");
            player.setItemSlot(EquipmentSlot.LEGS, root);
            hit(player, player.damageSources().lava(), variant.equals("routing") ? 20 : 16.8F);
            hit(player, player.damageSources().flyIntoWall(), variant.equals("routing") ? 20 : 16);
            hit(player, player.damageSources().generic(), 20);
            hit(player, player.damageSources().fall(), 20);
            var fall = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(fall);
            require(!fall.isCanceled(), "Incoming support unexpectedly enabled mixed Bounce query gameplay");
            require(SilentGearDefense.retaliationLevel(2, null, true) == 2, "Incoming support changed retaliation level");
            if (!variant.equals("routing")) {
                var effect = api.admittedEffect(heatTrait);
                float amount = api.incoming(heatTrait, effect, root.copy(), player, player.damageSources().lava(), 12, 20);
                require(Math.abs(amount - 8.8F) < .0001F, "Guarded native leaf lost common-base arithmetic");
            }
            // A refused own route must never delegate to full native activateTraits.
            var event = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().lava(), 20));
            Object unchanged = SilentGearDefense.incoming(root, 20F, null,
                    arguments -> { throw new AssertionError("Mixed root reached full native trait dispatcher"); }, event, 20, false);
            require(unchanged.equals(20F) && ItemStack.matches(before, root), "Refused mixed routing changed root/history");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }
        var duplicates = FoundationChecks.paid(player, lined, lined.copy());
        require(selected(api, duplicates.getFirst()).stream().filter(t -> t.getTraitId().toString().equals("silentgear:bounce")).count() == 1,
                "Duplicate sources became two Bounce entries");

        if (Boolean.getBoolean("leggings.test.boots")) {
            sibling(player, lined);
        }
        if (!variant.equals("routing")) {
            DefenseIncomingChecks.run(player.level().getServer(), report);
            if (variant.equals("heat-definition") || variant.equals("callback")) {
                failure(player, lined, variant, report);
            }
        }
        report.append("Defense A/B: native Heat IV/slime-lining Bounce I; live exact definitions/common-base leaf; paid inverse/duplicate source admission; ")
                .append("production incoming active; Bounce fall/retaliation queries remain inert; independent variant=").append(variant)
                .append("; actual order=").append(DefenseTestPlugin.actualOrder).append('\n');
    }

    private static void failure(ServerPlayer player, ItemStack lined, String variant, StringBuilder report) throws Exception {
        DefenseIncomingChecks.clear(player);
        var root = FoundationChecks.paid(player, lined, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        var before = root.copy();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        if (variant.equals("callback")) {
            DefenseProbe.calls = 0;
            DefenseProbe.failSecond = true;
            try {
                // Bounce reduces the running wall hit first; failing Heat must not publish that partial result.
                hit(player, player.damageSources().flyIntoWall(), 20);
                require(DefenseProbe.calls == 2 && !SilentGearDefense.incomingAvailable(), "Failed callback did not revoke the incoming flow");
            } finally {
                DefenseProbe.failSecond = false;
            }
            DefenseProbe.calls = 0;
            hit(player, player.damageSources().flyIntoWall(), 20);
            require(DefenseProbe.calls == 0, "Refused incoming flow retried native callbacks");
        } else {
            var heat = trait(lined, "heat_resistant").getTrait();
            Field effects = field(heat.getClass(), "effects");
            Object prior = effects.get(heat);
            try {
                effects.set(heat, List.of(new Object()));
                hit(player, player.damageSources().lava(), 20);
                hit(player, player.damageSources().flyIntoWall(), 16);
            } finally {
                effects.set(heat, prior);
            }
            hit(player, player.damageSources().lava(), 20); // Process-lifetime refusal is not rearmed by restoring data.
            DefenseProbe.calls = 0;
            hit(player, player.damageSources().flyIntoWall(), 16);
            require(DefenseProbe.calls == 1 && SilentGearDefense.incomingAvailable(), "Refusing Heat also disabled admitted Bounce");
        }
        require(SilentGearDefense.fallAvailable() && SilentGearDefense.retaliationAvailable()
                && ItemStack.matches(before, root), "Incoming failure leaked to independent query contracts/root data");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        report.append("Incoming B failure: ").append(variant)
                .append("; no partial publication/replay, bounded independent refusal and unchanged root passed\n");
    }

    private static void definitions(SilentGearDefenseApi api, TraitInstance heat, TraitInstance bounce) throws Throwable {
        Object negate = api.admittedEffect(heat);
        api.admittedEffect(bounce);
        Field effects = field(heat.getTrait().getClass(), "effects");
        Object original = effects.get(heat.getTrait());
        try {
            for (var unsupported : List.of(List.of(), List.of(negate), List.of(negate, negate), List.of(negate, new Object()))) {
                effects.set(heat.getTrait(), unsupported);
                rejected(() -> api.admittedEffect(heat));
                api.admittedEffect(bounce); // Refusing Heat's definition does not invalidate Bounce's definition.
            }
        } finally {
            effects.set(heat.getTrait(), original);
        }
        Field scale = field(negate.getClass(), "negatedDamageScale");
        float prior = scale.getFloat(negate);
        try {
            for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1}) {
                scale.setFloat(negate, bad);
                rejected(() -> api.admittedEffect(heat));
            }
            scale.setFloat(negate, .08F);
            api.validate(heat, negate, 12, 20); // Current finite native balance changes remain admissible.
            scale.setFloat(negate, .5F);
            rejected(() -> api.validate(heat, negate, 12, Float.MAX_VALUE));
        } finally {
            scale.setFloat(negate, prior);
        }
        rejected(() -> api.validate(heat, api.admittedEffect(bounce), 20, 20));
        rejected(() -> api.validate(heat, negate, Float.NaN, 20));
        require(api.admittedEffect(heat) == negate, "Restored current definition was not re-admitted");
    }

    private static void sibling(ServerPlayer player, ItemStack lined) throws Exception {
        for (String group : List.of("Incoming", "SafeFall", "BounceFall", "Retaliation")) {
            require(ready("event.GearEvents", "mismatchedboots$sg" + group + "Ready"), "Boots defensive group lost: " + group);
        }
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var gear = (GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"));
        var nativeBoots = gear.construct(List.of(PartInstance.from(FoundationChecks.part("boot_plates", "crimson_iron"))));
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        var mixedLegs = FoundationChecks.paid(player, lined, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, root);
        hit(player, player.damageSources().lava(), 16.8F);
        var feetBefore = root.copy();
        for (var legs : mixedLegs) {
            var legsBefore = legs.copy();
            player.setItemSlot(EquipmentSlot.LEGS, legs);
            hit(player, player.damageSources().lava(), SilentGearDefense.incomingAvailable() ? 13.6F : 16.8F);
            require(ItemStack.matches(feetBefore, root) && ItemStack.matches(legsBefore, legs), "Joint mixed incoming changed either root/history");
        }
        var plainPair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                .invoke(null, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        var plain = (ItemStack) plainPair.getClass().getMethod("primary").invoke(plainPair);
        player.setItemSlot(EquipmentSlot.FEET, plain);
        player.setItemSlot(EquipmentSlot.LEGS, lined.copy());
        var before = plain.copy();
        var fall = new LivingFallEvent(player, 12, 1);
        NeoForge.EVENT_BUS.post(fall);
        before.setDamageValue(before.getDamageValue() + 3);
        require(fall.isCanceled() && ItemStack.matches(before, plain), "Native LEGS peer lost Boots' ordinary safe FEET bill");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
    }

    private static List<TraitInstance> selected(SilentGearDefenseApi api, ItemStack root) throws Exception {
        var method = SilentGearDefenseApi.class.getDeclaredMethod("traits", ItemStack.class);
        method.setAccessible(true);
        var found = new ArrayList<TraitInstance>();
        for (var value : (List<?>) method.invoke(api, root)) {
            var getter = value.getClass().getDeclaredMethod("nativeInstance");
            getter.setAccessible(true);
            found.add((TraitInstance) getter.invoke(value));
        }
        return found;
    }

    private static TraitInstance trait(ItemStack stack, String name) {
        return TraitHelper.getTraits(stack).stream().filter(t -> t.getTraitId().toString().equals("silentgear:" + name)).findFirst().orElseThrow();
    }

    private static void hit(ServerPlayer player, DamageSource source, float expected) {
        var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, 20));
        NeoForge.EVENT_BUS.post(event);
        require(Math.abs(event.getAmount() - expected) < .0001F, "Wrong native/inert incoming amount: " + event.getAmount() + ", expected " + expected);
    }

    private static boolean ready(String owner, String name) throws Exception {
        var method = Class.forName("net.silentchaos512.gear." + owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void rejected(Checked action) throws Throwable {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Unsupported defensive definition/arithmetic admitted");
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Throwable;
    }
}
