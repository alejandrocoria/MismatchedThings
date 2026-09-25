package games.alejandrocoria.mismatchedboots.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public final class DamageTagReloadGameTests {
    private static final String ENABLED_PROPERTY = "mismatchedboots.test.damageTagReload";
    private static final String PACK_NAME = "mismatchedboots-damage-tag-reload";
    private static final String PACK_ID = "file/" + PACK_NAME;
    private static final TagKey<DamageType> RESISTANCE_TAG = TagKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("mismatchedboots-test", "reload_resistance"));

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE, maxTicks = 1200)
    public void existingStackTracksTagReload(GameTestHelper helper) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            helper.succeed();
            return;
        }

        MinecraftServer server = helper.getLevel().getServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
        try {
            writePackMetadata(pack);
            writeDamageTypes(pack, "minecraft:in_fire");
            server.getPackRepository().reload();
            helper.assertTrue(server.getPackRepository().isAvailable(PACK_ID), "Test data pack was not discovered");
            ArrayList<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            selected.add(PACK_ID);
            server.reloadResources(selected).join();

            ItemStack first = new ItemStack(Items.IRON_BOOTS);
            DamageResistant resistance = new DamageResistant(
                    helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                            .getOrThrow(RESISTANCE_TAG));
            first.set(DataComponents.DAMAGE_RESISTANT, resistance);
            ItemStack second = new ItemStack(Items.GOLDEN_BOOTS);
            second.set(DataComponents.DAMAGE_RESISTANT, resistance);
            var pair = MixedBootsFactory.createPair(first, second);
            ItemStack existing = pair.primary();
            ItemStack inverse = pair.inverse();
            assertDamageBehavior(helper, existing, true, false, "before data-pack reload");
            assertDamageBehavior(helper, inverse, true, false, "inverse before data-pack reload");
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, existing).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            assertDamageBehavior(helper, decoded, true, false, "decoded before data-pack reload");

            writeDamageTypes(pack, "minecraft:cactus");
            server.reloadResources(selected).join();

            helper.assertTrue(existing.get(DataComponents.DAMAGE_RESISTANT) == resistance,
                    "Reload replaced the existing stack component");
            assertDamageBehavior(helper, existing, false, true, "after data-pack reload");
            assertDamageBehavior(helper, inverse, false, true, "inverse after data-pack reload");
            assertDamageBehavior(helper, decoded, false, true, "decoded after data-pack reload");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare the damage-tag data pack", exception);
        }
    }

    private static void writePackMetadata(Path pack) throws IOException {
        PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"description":"MismatchedBoots damage-tag reload test",
                "min_format":[%d,%d],"max_format":[%d,%d]}}
                """.formatted(version.major(), version.minor(), version.major(), version.minor()),
                StandardCharsets.UTF_8);
    }

    private static void writeDamageTypes(Path pack, String damageType) throws IOException {
        Path tag = pack.resolve("data/mismatchedboots-test/tags/damage_type/reload_resistance.json");
        Files.createDirectories(tag.getParent());
        Files.writeString(tag, "{\"replace\":true,\"values\":[\"" + damageType + "\"]}",
                StandardCharsets.UTF_8);
    }

    private static void assertDamageBehavior(
            GameTestHelper helper, ItemStack stack, boolean resistsInFire, boolean resistsCactus, String stage) {
        var level = helper.getLevel();
        helper.assertValueEqual(stack.canBeHurtBy(level.damageSources().inFire()), !resistsInFire,
                stage + " in-fire stack damage");
        helper.assertValueEqual(stack.canBeHurtBy(level.damageSources().cactus()), !resistsCactus,
                stage + " cactus stack damage");
        helper.assertTrue(stack.canBeHurtBy(level.damageSources().lava()),
                stage + " lava stack damage");

        ItemEntity fireItem = new ItemEntity(level, 0, 0, 0, stack.copy());
        helper.assertValueEqual(fireItem.fireImmune(), resistsInFire, stage + " item-entity fire immunity");
        helper.assertValueEqual(fireItem.hurtServer(level, level.damageSources().inFire(), 1.0F), !resistsInFire,
                stage + " item-entity in-fire damage");

        ItemEntity cactusItem = new ItemEntity(level, 0, 0, 0, stack.copy());
        helper.assertValueEqual(cactusItem.hurtServer(level, level.damageSources().cactus(), 1.0F), !resistsCactus,
                stage + " item-entity cactus damage");

        // Entity.fireImmune broadens in-fire stack resistance to all fire damage, including lava.
        ItemEntity lavaItem = new ItemEntity(level, 0, 0, 0, stack.copy());
        helper.assertValueEqual(lavaItem.hurtServer(level, level.damageSources().lava(), 1.0F), !resistsInFire,
                stage + " item-entity lava damage");
    }
}
