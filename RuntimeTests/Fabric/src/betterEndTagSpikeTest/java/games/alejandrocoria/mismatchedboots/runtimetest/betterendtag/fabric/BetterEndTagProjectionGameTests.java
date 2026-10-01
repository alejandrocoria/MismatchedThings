package games.alejandrocoria.mismatchedboots.runtimetest.betterendtag.fabric;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class BetterEndTagProjectionGameTests {
    private static final String EMPTY_STRUCTURE = "mismatchedboots_betterend_tag_spike_test:empty";
    private static final String PACK_NAME = "mismatchedboots-betterend-tag-spike";
    private static final String PACK_ID = "file/" + PACK_NAME;
    private static final TagKey<Item> CRYSTALITE_SET = tag("betterend", "armor_set/crystalite");
    private static final TagKey<Item> UNAUDITED_BETTER_END = tag("betterend", "unaudited_control");

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 1200)
    public void crystaliteProjectionIsExactAndReloadable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack crystalite = item("betterend", "crystalite_boots").getDefaultInstance();
        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystalite, Items.IRON_BOOTS.getDefaultInstance());
        ItemStack existing = pair.primary();
        ItemStack unchangedRoot = existing.copy();
        MixedBootsData unchangedSources = mixedData(existing);

        equipCrystaliteArmor(player, crystalite);
        require(crystalite.is(CRYSTALITE_SET),
                "Ordinary Crystalite Boots are missing the exact set tag");
        require(hasFullCrystaliteSet(player),
                "Better End rejected the ordinary full Crystalite set");
        assertProjectedSet(player, pair.primary(), "primary");
        assertProjectedSet(player, pair.inverse(), "inverse");

        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        require(!unrelated.primary().is(CRYSTALITE_SET),
                "Unrelated Mismatched sources received Crystalite set identity");
        require(crystalite.is(UNAUDITED_BETTER_END),
                "Crystalite source is missing the unaudited Better End control tag");
        require(!existing.is(UNAUDITED_BETTER_END),
                "The spike projected an unaudited Better End tag");
        require(crystalite.is(ItemTags.FOOT_ARMOR),
                "Crystalite source is missing its vanilla foot-armor tag");
        require(!existing.is(ItemTags.FOOT_ARMOR),
                "The spike projected an unaudited vanilla tag");

        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
        try {
            writePackMetadata(pack);
            writeCrystaliteSet(pack);
            ArrayList<String> selected = selectPack(server);
            server.reloadResources(selected).join();

            require(!existing.is(CRYSTALITE_SET),
                    "Existing stack retained Crystalite identity after its source left the tag");
            player.setItemSlot(EquipmentSlot.FEET, existing);
            require(!hasFullCrystaliteSet(player),
                    "Mixed boots completed the set without a qualifying source");
            assertUnchanged(existing, unchangedRoot, unchangedSources, "Crystalite removal");

            writeCrystaliteSet(pack, "minecraft:iron_boots");
            server.reloadResources(selected).join();

            require(existing.is(CRYSTALITE_SET),
                    "Existing stack did not follow its iron source into the reloaded tag");
            require(hasFullCrystaliteSet(player),
                    "Better End rejected the reloaded qualifying mixed boots");
            MixedBootsData liveSources = mixedData(existing);
            require(!liveSources.left().source().is(CRYSTALITE_SET),
                    "Crystalite source unexpectedly remained in the replacement tag");
            require(liveSources.right().source().is(CRYSTALITE_SET),
                    "Iron source did not enter the replacement tag");
            require(!existing.is(UNAUDITED_BETTER_END),
                    "Reload caused an unaudited Better End tag to leak from a source");
            require(!existing.is(ItemTags.FOOT_ARMOR),
                    "Reload caused an unaudited vanilla tag to leak from a source");
            assertUnchanged(existing, unchangedRoot, unchangedSources, "iron replacement");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare the Crystalite tag data pack", exception);
        }
    }

    private static void assertProjectedSet(Player player, ItemStack mixed, String orientation) {
        require(mixed.is(CRYSTALITE_SET),
                orientation + " Mismatched root did not project Crystalite set identity");
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(hasFullCrystaliteSet(player),
                orientation + " Mismatched root did not complete the Crystalite set");
    }

    private static void equipCrystaliteArmor(Player player, ItemStack boots) {
        player.setItemSlot(EquipmentSlot.HEAD, item("betterend", "crystalite_helmet").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.CHEST, item("betterend", "crystalite_chestplate").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.LEGS, item("betterend", "crystalite_leggings").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    private static boolean hasFullCrystaliteSet(Player player) {
        try {
            Class<?> armor = Class.forName(
                    "org.betterx.betterend.item.CrystaliteArmor",
                    false,
                    BetterEndTagProjectionGameTests.class.getClassLoader());
            Method method = armor.getMethod("hasFullSet", LivingEntity.class);
            return (boolean) method.invoke(null, player);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke Better End's full-set predicate", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Better End's full-set predicate failed", exception.getCause());
        }
    }

    private static ArrayList<String> selectPack(MinecraftServer server) {
        server.getPackRepository().reload();
        require(server.getPackRepository().isAvailable(PACK_ID),
                "Crystalite tag test data pack was not discovered");
        ArrayList<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.remove(PACK_ID);
        selected.add(PACK_ID);
        return selected;
    }

    private static void writePackMetadata(Path pack) throws IOException {
        PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"description":"MismatchedBoots Crystalite tag spike",
                "min_format":[%d,%d],"max_format":[%d,%d]}}
                """.formatted(version.major(), version.minor(), version.major(), version.minor()),
                StandardCharsets.UTF_8);
    }

    private static void writeCrystaliteSet(Path pack, String... boots) throws IOException {
        Path tag = pack.resolve("data/betterend/tags/item/armor_set/crystalite.json");
        Files.createDirectories(tag.getParent());
        ArrayList<String> values = new ArrayList<>(Arrays.asList(
                "betterend:crystalite_helmet",
                "betterend:crystalite_chestplate",
                "betterend:crystalite_leggings"));
        values.addAll(Arrays.asList(boots));
        String encoded = values.stream()
                .map(item -> "\"" + item + "\"")
                .collect(Collectors.joining(","));
        Files.writeString(tag, "{\"replace\":true,\"values\":[" + encoded + "]}",
                StandardCharsets.UTF_8);
    }

    private static void assertUnchanged(
            ItemStack existing,
            ItemStack expectedRoot,
            MixedBootsData expectedSources,
            String stage) {
        require(ItemStack.matches(expectedRoot, existing), stage + " changed root item data");
        require(expectedSources.equals(mixedData(existing)), stage + " changed source snapshots");
    }

    private static MixedBootsData mixedData(ItemStack stack) {
        return ModContent.getMixedBootsData(stack)
                .orElseThrow(() -> new AssertionError("Missing mixed-boots source data"));
    }

    private static Item item(String namespace, String path) {
        Identifier identifier = Identifier.fromNamespaceAndPath(namespace, path);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        require(identifier.equals(BuiltInRegistries.ITEM.getKey(item)),
                "Missing exact Better End item " + identifier);
        return item;
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
