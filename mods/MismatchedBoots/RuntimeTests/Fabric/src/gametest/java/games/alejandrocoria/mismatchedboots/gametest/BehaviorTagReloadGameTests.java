package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class BehaviorTagReloadGameTests {
    private static final String ENABLED_PROPERTY = "mismatchedboots.test.behaviorTagReload";
    private static final String PACK_NAME = "mismatchedboots-behavior-tag-reload";
    private static final String PACK_ID = "file/" + PACK_NAME;

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE, maxTicks = 1200)
    public void existingStackTracksPiglinSafeArmorReload(GameTestHelper helper) {
        if (!GameTestContexts.requireOrSkip(helper, ENABLED_PROPERTY, Boolean.getBoolean(ENABLED_PROPERTY))) {
            return;
        }

        MinecraftServer server = helper.getLevel().getServer();
        ItemStack existing = GameTestStacks.mixed(Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        ItemStack unchangedRoot = existing.copy();
        MixedBootsData unchangedSources = GameTestStacks.data(existing);
        helper.assertTrue(existing.is(ItemTags.PIGLIN_SAFE_ARMOR),
                          "Default golden source did not grant piglin safety");

        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
        try {
            writePackMetadata(pack);
            writePiglinSafeArmor(pack);
            ArrayList<String> selected = selectPack(helper, server);
            server.reloadResources(selected).join();

            helper.assertTrue(!existing.is(ItemTags.PIGLIN_SAFE_ARMOR),
                              "Existing stack retained piglin safety after replacing the tag with an empty set");
            assertUnchanged(helper, existing, unchangedRoot, unchangedSources, "empty replacement");

            writePiglinSafeArmor(pack, "minecraft:iron_boots");
            server.reloadResources(selected).join();

            helper.assertTrue(existing.is(ItemTags.PIGLIN_SAFE_ARMOR),
                              "Existing stack did not follow its iron source into the reloaded tag");
            MixedBootsData liveSources = GameTestStacks.data(existing);
            helper.assertTrue(!liveSources.left().source().is(ItemTags.PIGLIN_SAFE_ARMOR),
                              "Golden source unexpectedly remained in the replacement tag");
            helper.assertTrue(liveSources.right().source().is(ItemTags.PIGLIN_SAFE_ARMOR),
                              "Iron source did not enter the replacement tag");
            assertUnchanged(helper, existing, unchangedRoot, unchangedSources, "iron replacement");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare the behavior-tag data pack", exception);
        }
    }

    private static ArrayList<String> selectPack(GameTestHelper helper, MinecraftServer server) {
        server.getPackRepository().reload();
        helper.assertTrue(server.getPackRepository().isAvailable(PACK_ID),
                          "Behavior-tag test data pack was not discovered");
        ArrayList<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.remove(PACK_ID);
        selected.add(PACK_ID);
        return selected;
    }

    private static void writePackMetadata(Path pack) throws IOException {
        PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                                  {"pack":{"description":"MismatchedBoots behavior-tag reload test",
                                  "min_format":[%d,%d],"max_format":[%d,%d]}}
                                  """.formatted(version.major(), version.minor(), version.major(), version.minor()),
                          StandardCharsets.UTF_8);
    }

    private static void writePiglinSafeArmor(Path pack, String... items) throws IOException {
        Path tag = pack.resolve("data/minecraft/tags/item/piglin_safe_armor.json");
        Files.createDirectories(tag.getParent());
        String values = Arrays.stream(items)
                              .map(item -> "\"" + item + "\"")
                              .collect(Collectors.joining(","));
        Files.writeString(tag, "{\"replace\":true,\"values\":[" + values + "]}",
                          StandardCharsets.UTF_8);
    }

    private static void assertUnchanged(
            GameTestHelper helper,
            ItemStack existing,
            ItemStack expectedRoot,
            MixedBootsData expectedSources,
            String stage) {
        helper.assertTrue(ItemStack.matches(expectedRoot, existing),
                          stage + " changed root item data");
        helper.assertTrue(expectedSources.equals(GameTestStacks.data(existing)),
                          stage + " changed source snapshots");
    }
}
