package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe;
import net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.spike.silentgear.SpikeChecks.armor;
import static games.alejandrocoria.mismatchedleggings.spike.silentgear.SpikeChecks.part;
import static games.alejandrocoria.mismatchedleggings.spike.silentgear.SpikeChecks.player;
import static games.alejandrocoria.mismatchedleggings.spike.silentgear.SpikeChecks.require;

/** Block A controls in the existing proof, never a second dispatcher or production command. */
public final class WearerContracts {
    static final List<String[]> FAMILIES = List.of(new String[]{"adamant", "nickel"}, new String[]{"aquatic", "lead"},
            new String[]{"flame_ward", "crimson_steel"}, new String[]{"stellar", "refined_iron"});
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
    public static boolean observing;

    private WearerContracts() {}

    static void run(MinecraftServer server, StringBuilder report) throws Throwable {
        var player = player(server);
        var api = SetCandidate.api();
        var calibration = armor("leggings", "legging_plates", "iron");
        boolean witnessed = false;
        observing = true;
        try { GearData.recalculateGearData(calibration, player); }
        catch (AssertionError expected) { witnessed = true; }
        finally { observing = false; }
        require(witnessed, "Wearer recalculation tripwire did not observe its positive control");
        for (var family : FAMILIES) {
            String name = family[0];
            var pieces = List.of(armor("leggings", "legging_plates", family[1]), armor("boots", "boot_plates", family[1]),
                    armor("helmet", "helmet_plates", family[1]), armor("chestplate", "chestplate_plates", family[1]));
            var instance = TraitHelper.getTraits(pieces.getFirst()).stream().filter(t -> t.getTraitId().getPath().equals(name)).findFirst().orElseThrow();
            var effect = (WielderEffectTraitEffect) api.admittedEffects(instance).getFirst();
            report.append("NATIVE ").append(name).append(" level=").append(instance.getLevel()).append(" effects=")
                    .append(api.effects(instance).stream().map(e -> e.getClass().getSimpleName()).toList()).append('\n');
            for (int count = 0; count <= 4; count++) {
                equip(player, pieces, count);
                var context = new TraitActionContext(player, instance, pieces.getFirst());
                require(((SetAccess) effect).spike$count(context, GearTypes.ARMOR.get(), player) == count, "Native armor count differs");
                require(((SetAccess) effect).spike$count(context, GearTypes.SWORD.get(), player) == 1, "Non-armor count must remain one");
                nativeTick(effect, player, instance, pieces.getFirst(), true);
                check(player, name, count, true);
                report.append("  count=").append(count).append(" potions=").append(player.getActiveEffects()).append('\n');
            }
            equip(player, pieces, 4);
            nativeTick(effect, player, instance, armor("sword", "sword_blade", family[1]), true);
            require(player.getActiveEffects().isEmpty(), "Armor potion definition reached a sword");
            effect.inventoryTick(new TraitActionContext(null, instance, pieces.getFirst()), player.level(), player, true);
            require(player.getActiveEffects().isEmpty(), "Native null-player context applied effects");
            for (var selectedSlot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                equip(player, pieces, 0);
                player.setItemSlot(selectedSlot, pieces.getFirst().copy());
                nativeTick(effect, player, instance, pieces.getFirst(), true);
                check(player, name, selectedSlot == EquipmentSlot.LEGS ? 1 : 0, true);
            }
            equip(player, pieces, 4);
            nativeTick(effect, player, instance, pieces.getFirst(), false);
            check(player, name, 4, false);
            player.tickCount = 21;
            nativeTick(effect, player, instance, pieces.getFirst(), true);
            check(player, name, 4, false);
            player.tickCount = 30;
            nativeTick(effect, player, instance, pieces.getFirst(), true);
            check(player, name, 4, true);
            var tickEffects = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("tickEffects");
            tickEffects.setAccessible(true);
            for (int tick = 0; tick < 40; tick++) { tickEffects.invoke(player); }
            require(player.getActiveEffects().isEmpty(), "Native potion did not expire naturally");
            // Current definitions are read again after a change, without executing the changed data.
            var potions = effect.getClass().getDeclaredField("potions");
            potions.setAccessible(true);
            Object original = potions.get(effect);
            try {
                potions.set(effect, Map.of());
                boolean refused = false;
                try { api.admittedEffects(instance); } catch (IllegalStateException expected) { refused = true; }
                require(refused, "Changed gear branches were admitted");
            } finally { potions.set(effect, original); }
            require(api.admittedEffects(instance).size() == 1, "Restored native definition was not revalidated");
            for (var root : List.of(MixedLeggingsFactory.createPair(pieces.getFirst(), new ItemStack(Items.DIAMOND_LEGGINGS)).primary(),
                    MixedLeggingsFactory.createPair(pieces.getFirst(), new ItemStack(Items.DIAMOND_LEGGINGS)).inverse())) {
                root.remove(SilentGearCompatibility.state()); // Read-only recognition must not require live state.
                var before = root.copy();
                observing = true;
                try {
                    var carrier = api.carrier(root);
                    require(carrier.getItem().getClass() == pieces.getFirst().getItem().getClass() && carrier != root,
                            "Carrier is not disposable native leggings");
                    require(api.recognizes(root, new TraitActionContext(player, instance, carrier)), "Missing-state root lost read-only recognition");
                    require(TraitHelper.getTraits(carrier).size() == api.traits(root).size(), "Carrier does not contain the effective trait view");
                    root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                    require(player.getActiveEffects().isEmpty(), "Block A activated production Wielder");
                    require(ItemStack.matches(root, before), "Read-only admission changed root/history/state");
                    boolean denied = false;
                    try { api.context(player, instance, root); } catch (IllegalArgumentException expected) { denied = true; }
                    require(denied, "Mixed root accepted as native context receiver");
                } finally { observing = false; }
            }
        }
        sourceView(player);
        brilliant(player);
        require(SpikePlugin.nativeProof.size() == 5 && SpikePlugin.nativeProof.values().stream().allMatch(Boolean.TRUE::equals), "Final native call/query proof failed: " + SpikePlugin.nativeProof);
        report.append("FINAL native boundaries ").append(SpikePlugin.nativeProof).append('\n');
        for (var ingredient : List.of("ingots/nickel", "ingots/lead", "ingots/refined_iron", "ingots/crimson_steel")) {
            report.append("INGREDIENT c:").append(ingredient).append('=');
            for (var item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse("c:" + ingredient)))) {
                report.append(BuiltInRegistries.ITEM.getKey(item.value())).append(' ');
            }
            report.append('\n');
        }
        manual(player);
        equip(player, List.of(), 0);
    }

    @SuppressWarnings("unchecked")
    private static void sourceView(ServerPlayer player) throws Throwable {
        var source = armor("leggings", "legging_plates", "refined_iron");
        var lower = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 1));
        var higher = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 4));
        var matching = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 5, new GearTypeTraitCondition(GearTypes.LEGGINGS)));
        var conditional = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 5, new GearTypeTraitCondition(GearTypes.BOOTS)));
        var api = SetCandidate.api();
        for (var second : List.of(higher, conditional, matching)) {
            var root = MixedLeggingsFactory.createPair(lower, second).primary();
            var before = root.copy();
            observing = true;
            try {
                var view = api.traits(root);
                require(view.size() == 1 && view.getFirst().level() == (second == higher ? 4 : second == matching ? 5 : 1), "Levels summed/duplicated or unmatched condition admitted");
                require(ItemStack.matches(before, root), "Computed trait query changed source history");
            } finally { observing = false; }
        }
        var root = MixedLeggingsFactory.createPair(lower, higher).primary();
        for (var invalid : List.of(new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID)), new ItemStack(Items.DIAMOND_LEGGINGS))) {
            require(api.traits(invalid).isEmpty() && api.carrier(invalid).isEmpty(), "Invalid/foreign root manufactured a carrier");
        }
        var disjoint = MixedLeggingsFactory.createPair(lower, armor("leggings", "legging_plates", "gold")).primary();
        var ids = api.traits(disjoint).stream().map(t -> t.id().toString()).toList();
        require(ids.equals(ids.stream().sorted().distinct().toList()) && ids.containsAll(List.of("silentgear:stellar", "silentgear:brilliant")),
                "Different source traits were lost, duplicated or unstable");
        var wrongSlot = root.copy();
        wrongSlot.set(DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.FEET).build());
        var foreign = new ItemStack(Items.DIAMOND_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var suspended = root.copy();
        var quarantined = root.copy();
        var type = (net.minecraft.core.component.DataComponentType<games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(LeggingsContent.SUSPENDED_LEGGINGS_ID);
        suspended.set(type, games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData.of(new net.minecraft.nbt.CompoundTag(),
                games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData.equipmentProjection(), new net.minecraft.nbt.CompoundTag()));
        quarantined.set(type, games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData.quarantined(new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag()));
        for (var invalid : List.of(wrongSlot, foreign, suspended, quarantined)) {
            var before = invalid.copy();
            require(api.traits(invalid).isEmpty() && api.carrier(invalid).isEmpty() && ItemStack.matches(before, invalid), "Invalid root was admitted or mutated");
        }
        root.setCount(2);
        require(api.traits(root).isEmpty() && api.carrier(root).isEmpty(), "Multiple roots manufactured a carrier");
    }

    private static ItemStack only(ItemStack source, TraitInstance trait) {
        var copy = source.copy();
        var map = new HashMap<>(GearData.getProperties(copy).properties());
        map.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(trait)));
        copy.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(map));
        return copy;
    }

    private static void brilliant(ServerPlayer player) throws Throwable {
        var nativeGold = armor("leggings", "legging_plates", "gold");
        var plain = armor("leggings", "legging_plates", "iron");
        require(nativeGold.getItem().makesPiglinsNeutral(nativeGold, player), "Native Brilliant control failed");
        require(!plain.getItem().makesPiglinsNeutral(plain, player), "Native plain armor grants Brilliant");
        for (var other : List.of(new ItemStack(Items.DIAMOND_LEGGINGS), nativeGold)) {
            var pair = MixedLeggingsFactory.createPair(nativeGold, other);
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var before = root.copy();
                observing = true;
                try {
                    var carrier = new games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearerApi.Carriers().carrier(root);
                    var carrierBefore = carrier.copy();
                    require(carrier.getItem().makesPiglinsNeutral(carrier, player), "Effective native carrier lost Brilliant");
                    require(!root.getItem().makesPiglinsNeutral(root, player), "Block A activated production Brilliant");
                    require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Brilliant query mutated gear");
                } finally { observing = false; }
            }
        }
    }

    private static void nativeTick(WielderEffectTraitEffect effect, ServerPlayer player, TraitInstance trait, ItemStack carrier, boolean selected) {
        var before = carrier.copy();
        var equipment = SLOTS.stream().map(s -> player.getItemBySlot(s).copy()).toList();
        observing = true;
        try { effect.inventoryTick(new TraitActionContext(player, trait, carrier), player.level(), player, selected); }
        finally { observing = false; }
        require(ItemStack.matches(before, carrier), "Native Wielder changed carrier");
        for (int i = 0; i < SLOTS.size(); i++) { require(ItemStack.matches(equipment.get(i), player.getItemBySlot(SLOTS.get(i))), "Native Wielder changed equipment"); }
    }

    private static void equip(ServerPlayer player, List<ItemStack> pieces, int count) {
        player.removeAllEffects();
        player.tickCount = 20;
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        for (int i = 0; i < count; i++) { player.setItemSlot(SLOTS.get(i), pieces.get(i).copy()); }
    }

    private static void check(ServerPlayer player, String family, int count, boolean selected) {
        switch (family) {
            case "adamant" -> potion(player, MobEffects.RESISTANCE, selected ? count == 4 ? 2 : 1 : 0);
            case "aquatic" -> potion(player, MobEffects.WATER_BREATHING, selected && count == 4 ? 1 : 0);
            case "flame_ward" -> potion(player, MobEffects.FIRE_RESISTANCE, selected && count == 4 ? 1 : 0);
            case "stellar" -> {
                potion(player, MobEffects.SPEED, selected ? Math.max(0, count - 1) : 0);
                potion(player, MobEffects.JUMP_BOOST, selected ? Math.max(1, count) : 0);
            }
            default -> throw new AssertionError(family);
        }
    }

    private static void potion(ServerPlayer player, Holder<MobEffect> type, int level) {
        var actual = player.getEffect(type);
        require((actual == null ? 0 : actual.getAmplifier() + 1) == level, "Native potion level differs: " + type + " " + actual);
        if (actual != null) { require(actual.getDuration() == 38 && actual.isAmbient() && !actual.isVisible(), "Native potion duration/visibility differs"); }
    }

    private static void manual(ServerPlayer player) throws Exception {
        var commands = new StringBuilder("Native API-created materials; these are NOT claimed stock survival recipes.\n"
                + "Nickel/lead/refined iron ingredient tags are empty in the minimal SG/Lib profile.\n"
                + "In a test world with cheats, take each ORIGINAL Silent Gear iron armor piece from creative examples.\n"
                + "Run its give command, then craft the original piece + the given plate BEFORE mixing leggings.\n"
                + "Every command and native part-swap result below is verified by this run.\n\n");
        var families = new java.util.ArrayList<>(FAMILIES);
        families.add(new String[]{"brilliant", "gold"});
        for (var family : families) {
            for (var piece : List.of(new String[]{"leggings", "legging_plates"}, new String[]{"boots", "boot_plates"},
                    new String[]{"helmet", "helmet_plates"}, new String[]{"chestplate", "chestplate_plates"})) {
                var part = part(piece[1], family[1]);
                var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var data = ItemStack.CODEC.encodeStart(ops, part).getOrThrow().getAsJsonObject().getAsJsonObject("components").get("silentgear:material_list");
                String snbt = data.toString().replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\":", "$1:");
                String command = "give @s silentgear:" + piece[1] + "[silentgear:material_list=" + snbt + "]";
                require(command.length() + 1 <= 256, "Manual part command exceeds chat limit");
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), command);
                var input = CraftingInput.of(2, 1, List.of(armor(piece[0], piece[1], "iron"), player.getMainHandItem()));
                require(GearPartSwapRecipe.INSTANCE.matches(input, player.level()), "Manual native part swap refused");
                var result = GearPartSwapRecipe.INSTANCE.assemble(input);
                require(TraitHelper.getTraits(result).stream().anyMatch(t -> t.getTraitId().getPath().equals(family[0])), "Manual command did not produce its native trait");
                commands.append(family[0]).append(' ').append(piece[0]).append('\n').append('/').append(command).append('\n');
            }
        }
        player.getInventory().clearContent();
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), "wearer-manual.txt"), commands);
    }
}
