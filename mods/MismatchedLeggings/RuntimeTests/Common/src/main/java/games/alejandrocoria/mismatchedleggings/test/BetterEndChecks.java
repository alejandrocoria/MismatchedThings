package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Reflection keeps native Better End optional on every ordinary fixture classpath. */
final class BetterEndChecks {
    static final TagKey<Item> CRYSTALITE = TagKey.create(Registries.ITEM,
            Identifier.parse("betterend:armor_set/crystalite"));

    private BetterEndChecks() {
    }

    static void verify(MinecraftServer server) throws Exception {
        var player = RepairFixtures.player(server);
        var roots = pair(nativeStack("crystalite_leggings"), new ItemStack(Items.IRON_LEGGINGS));
        setChecks(player, roots);
        health(player, nativeStack("crystalite_leggings"), 4);
        for (var root : roots) {
            health(player, root, 2);
        }
        for (var root : pair(nativeStack("crystalite_leggings"), nativeStack("crystalite_leggings"))) {
            health(player, root, 4);
            setAnswer(player, root, true);
        }
        for (String tier : List.of("thallasium", "terminite", "aeternium")) {
            for (var root : pair(nativeStack(tier + "_leggings"), new ItemStack(Items.IRON_LEGGINGS))) {
                setAnswer(player, root, false);
            }
        }
        invalid(player, roots.getFirst());
        smithing(server, nativeStack("terminite_leggings"));
        for (var root : pair(nativeStack("terminite_leggings"), new ItemStack(Items.IRON_LEGGINGS))) {
            smithing(server, root);
        }
        reload(server, player, roots);
        clearArmor(player);
    }

    static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults());
        return List.of(pair.primary(), pair.inverse());
    }

    static ItemStack nativeStack(String name) {
        var id = Identifier.fromNamespaceAndPath("betterend", name);
        var item = BuiltInRegistries.ITEM.getValue(id);
        require(item != null && BuiltInRegistries.ITEM.getKey(item).equals(id), "Missing pinned native item: " + id);
        return item.getDefaultInstance();
    }

    static void equipSet(Player player, ItemStack leggings, ItemStack boots) {
        clearArmor(player);
        player.setItemSlot(EquipmentSlot.HEAD, nativeStack("crystalite_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, nativeStack("crystalite_chestplate"));
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    static void clearArmor(Player player) {
        for (var slot : EquipmentSlotGroup.ARMOR) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static boolean hasFullSet(Player player) throws ReflectiveOperationException {
        return (boolean) Class.forName("org.betterx.betterend.item.CrystaliteArmor")
                .getMethod("hasFullSet", LivingEntity.class).invoke(null, player);
    }

    private static void setChecks(Player player, List<ItemStack> roots) throws ReflectiveOperationException {
        setAnswer(player, nativeStack("crystalite_leggings"), true);
        for (var root : roots) {
            setAnswer(player, root, true);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            regeneration(player, false);
        }
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.pair(nativeStack("crystalite_boots"), new ItemStack(Items.IRON_BOOTS));
            for (var bootRoot : boots) {
                for (var root : roots) {
                    equipSet(player, root, bootRoot);
                    regeneration(player, true);
                }
            }
        }
    }

    private static void setAnswer(Player player, ItemStack stack, boolean expected) throws ReflectiveOperationException {
        var before = stack.copy();
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        var left = data == null ? ItemStack.EMPTY : data.left().source().copy();
        var right = data == null ? ItemStack.EMPTY : data.right().source().copy();
        require(stack.is(CRYSTALITE) == expected, "Unexpected Crystalite membership: " + stack);
        equipSet(player, stack, nativeStack("crystalite_boots"));
        regeneration(player, expected);
        require(ItemStack.matches(stack, before), "Set query/effect mutated its worn input");
        require(data == null || ItemStack.matches(left, data.left().source()) && ItemStack.matches(right, data.right().source()),
                "Set query/effect mutated stored source snapshots");
    }

    private static void regeneration(Player player, boolean expected) throws ReflectiveOperationException {
        require(hasFullSet(player) == expected, "Native Crystalite full-set predicate disagrees");
        player.removeEffect(MobEffects.REGENERATION);
        // Invoke the native effect-tick method, including Better End's injected set dispatch.
        var tickEffects = LivingEntity.class.getDeclaredMethod("tickEffects");
        tickEffects.setAccessible(true);
        player.tickCount = 64;
        tickEffects.invoke(player);
        var effect = player.getEffect(MobEffects.REGENERATION);
        require((effect != null) == expected, "Native set effect disagrees with equipped recognition");
        if (effect != null) {
            require(effect.getAmplifier() == 0 && effect.getDuration() == 79,
                    "Native Regeneration I changed amplifier or post-tick duration");
        }
    }

    private static void health(Player player, ItemStack leggings, double bonus) throws ReflectiveOperationException {
        clearArmor(player);
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        var update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        update.setAccessible(true);
        update.invoke(player);
        double actual = player.getAttributeValue(Attributes.MAX_HEALTH);
        require(Math.abs(actual - (20 + bonus)) < 0.000001, "Wrong equipped maximum health: " + actual);
    }

    private static void invalid(Player player, ItemStack root) throws ReflectiveOperationException {
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var unrelated = new ItemStack(Items.IRON_LEGGINGS);
        unrelated.set(LeggingsContent.mixedLeggingsDataType(), MixedLeggingsStructure.validData(root).orElseThrow());
        for (var invalid : List.of(new ItemStack(root.getItem()), root.copyWithCount(2), malformed, suspended, unrelated)) {
            setAnswer(player, invalid, false);
        }
        equipSet(player, ItemStack.EMPTY, nativeStack("crystalite_boots"));
        player.getInventory().add(root.copy());
        regeneration(player, false);
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.FEET, root);
        regeneration(player, false);
    }

    private static void smithing(MinecraftServer server, ItemStack input) {
        var player = RepairFixtures.player(server);
        var original = input.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(nativeStack("plate_upgrade_smithing_template").copyWithCount(2));
        menu.getSlot(1).set(input.copy());
        menu.getSlot(2).set(nativeStack("aeternium_forged_plate").copyWithCount(2));
        menu.createResult();
        var preview = menu.getSlot(3).getItem().copy();
        require(!preview.isEmpty(), "Native Aeternium upgrade produced no result");
        var data = MixedLeggingsStructure.validData(preview).orElse(null);
        var source = MixedLeggingsStructure.validData(input).orElse(null);
        var aeternium = nativeStack("aeternium_leggings").getItem();
        if (source == null) {
            require(data == null && preview.is(aeternium), "Native control did not become Aeternium");
        } else {
            require(data != null, "Smithing replaced mixed leggings with a native item or invalid shell");
            require(data.left().source().is(source.left().source().is(Items.IRON_LEGGINGS) ? Items.IRON_LEGGINGS : aeternium)
                            && data.right().source().is(source.right().source().is(Items.IRON_LEGGINGS) ? Items.IRON_LEGGINGS : aeternium),
                    "Aeternium upgrade transformed the wrong ordered source");
        }
        var taken = menu.getSlot(3).safeTake(1, 1, player);
        require(!taken.isEmpty() && ItemStack.matches(taken, preview) && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
                "Aeternium upgrade did not pay for exactly one transformed source");
        require(ItemStack.matches(original, input), "Smithing mutated the input snapshot");
    }

    private static void reload(MinecraftServer server, Player player, List<ItemStack> roots) throws Exception {
        var originals = roots.stream().map(ItemStack::copy).toList();
        var selected = List.copyOf(server.getPackRepository().getSelectedIds());
        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("better-end-tags");
        var tag = pack.resolve("data/betterend/tags/item/armor_set/crystalite.json");
        Files.createDirectories(tag.getParent());
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"description":"Crystalite tag regression","min_format":[%d,%d],"max_format":[%d,%d]}}
                """.formatted(format.major(), format.minor(), format.major(), format.minor()));
        Files.writeString(tag, "{\"replace\":true,\"values\":[]}");
        server.getPackRepository().reload();
        var changed = new ArrayList<>(selected);
        changed.add("file/better-end-tags");
        try {
            server.reloadResources(changed).join();
            setAnswer(player, nativeStack("crystalite_leggings"), false);
            for (var root : roots) {
                setAnswer(player, root, false);
            }
            // A real datapack positive still wins, even for a bare shell with no projected source.
            Files.writeString(tag, "{\"replace\":true,\"values\":[\"" + LeggingsContent.MIXED_LEGGINGS_ID + "\"]}");
            server.reloadResources(changed).join();
            require(new ItemStack(roots.getFirst().getItem()).is(CRYSTALITE), "Projection suppressed a native tag positive");
        } finally {
            server.reloadResources(selected).join();
        }
        setChecks(player, roots);
        for (int index = 0; index < roots.size(); index++) {
            require(ItemStack.matches(originals.get(index), roots.get(index)), "Reload rewrote an existing mixed pair");
        }
    }
}
