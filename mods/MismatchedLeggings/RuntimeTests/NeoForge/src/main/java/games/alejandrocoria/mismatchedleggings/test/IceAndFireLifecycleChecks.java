package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.iceandfire.DamageChecks;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Store ordinary existing-schema roots in a real world; another process must read them unchanged. */
final class IceAndFireLifecycleChecks {
    private static final BlockPos BARREL = new BlockPos(0, 80, 0);
    private static boolean finished;
    private static LivingEntity observed;
    private static float before, after;
    private static int events;
    private static boolean witnesses;

    private IceAndFireLifecycleChecks() {}

    static void seed(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            var player = RepairFixtures.player(server);
            var roots = new ArrayList<ItemStack>();
            for (String path : List.of("tide_blue_leggings", "dragonsteel_lightning_leggings", "forest_troll_leather_leggings")) {
                var source = DamageChecks.nativeItem(path);
                var before = source.copy();
                var menu = new WorkbenchMenu(1, player.getInventory());
                menu.getSlot(0).set(source.copy());
                menu.getSlot(1).set(new ItemStack(Items.IRON_LEGGINGS));
                // This is the normal paid workbench, not a fabricated result.
                var first = menu.getSlot(2).safeTake(1, 1, player);
                var second = menu.getSlot(3).safeTake(1, 1, player);
                require(!first.isEmpty() && !second.isEmpty() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem()
                                && menu.getSlot(2).safeTake(1, 1, player).isEmpty(), "Composition payment/repeated take failed");
                require(ItemStack.matches(before, source), "Composition changed source input");
                for (var root : List.of(first, second)) {
                    require(MixedLeggingsStructure.validData(root).isPresent(), "Invalid native composition");
                    var damaged = root.copy();
                    damaged.setDamageValue(damaged.getMaxDamage() / 2);
                    var history = MixedLeggingsStructure.validData(damaged).orElseThrow();
                    var anvil = AnvilRepairChecks.ready(player, damaged.copy(), new ItemStack(Items.IRON_INGOT, 8));
                    int levels = player.experienceLevel, cost = anvil.getCost();
                    var repaired = anvil.getSlot(2).safeTake(1, 1, player);
                    require(!repaired.isEmpty() && repaired.getDamageValue() < damaged.getDamageValue() && cost > 0
                                    && player.experienceLevel == levels - cost && !anvil.getSlot(0).hasItem()
                                    && anvil.getSlot(1).getItem().getCount() < 8 && anvil.getSlot(2).safeTake(1, 1, player).isEmpty()
                                    && history.equals(MixedLeggingsStructure.validData(repaired).orElseThrow()), "Paid repair/history failed");
                    var trim = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
                    trim.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
                    trim.getSlot(1).set(repaired.copy());
                    trim.getSlot(2).set(new ItemStack(Items.DIAMOND, 2));
                    trim.createResult();
                    var result = trim.getSlot(3).safeTake(1, 1, player);
                    require(!result.isEmpty() && !trim.getSlot(1).hasItem() && trim.getSlot(0).getItem().getCount() == 1
                                    && trim.getSlot(2).getItem().getCount() == 1 && trim.getSlot(3).safeTake(1, 1, player).isEmpty(), "Paid trim failed");
                    var data = MixedLeggingsStructure.validData(result).orElseThrow();
                    require(data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM), "Trim missed source");
                    result.set(DataComponents.CUSTOM_NAME, Component.literal("Existing Ice and Fire root " + roots.size()));
                    roots.add(result);
                }
            }
            server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
            var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
            require(barrel != null, "Missing save barrel");
            var saved = new JsonArray();
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int i = 0; i < roots.size(); i++) {
                barrel.setItem(i, roots.get(i));
                saved.add(ItemStack.CODEC.encodeStart(ops, roots.get(i)).getOrThrow());
            }
            barrel.setChanged();
            var manifest = new JsonObject();
            manifest.addProperty("process", ProcessHandle.current().pid());
            manifest.add("roots", saved);
            Files.writeString(report("ice-and-fire-saved.json"), manifest.toString());
            Files.writeString(report("ice-and-fire-lifecycle.txt"), "3 paid compositions; 6 paid repairs/trims, both orientations; complete existing-schema roots stored in world barrel\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { server.halt(false); }
    }

    static List<ItemStack> reopen(MinecraftServer server) throws Exception {
        var manifest = JsonParser.parseString(Files.readString(report("ice-and-fire-saved.json"))).getAsJsonObject();
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Reopen must use another JVM");
        server.overworld().getChunk(0, 0);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "Saved barrel missing after reopen");
        var roots = new ArrayList<ItemStack>();
        var saved = manifest.getAsJsonArray("roots");
        require(saved.size() == 6, "Incomplete lifecycle roots");
        for (int i = 0; i < saved.size(); i++) {
            var expected = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), saved.get(i)).getOrThrow();
            require(ItemStack.matches(expected, barrel.getItem(i)), "Saved root/history changed: " + i);
            roots.add(barrel.getItem(i).copy());
        }
        if (!witnesses) {
            witnesses = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                    (LivingDamageEvent.Pre event) -> {
                        if (event.getEntity() == observed) { before = event.getNewDamage(); events++; }
                    });
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                    (LivingDamageEvent.Pre event) -> {
                        if (event.getEntity() == observed) { after = event.getNewDamage(); }
                    });
        }
        var damageTypes = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        // Actual native coefficients and health after deserialization, in each saved orientation.
        for (int i = 2; i < 6; i++) {
            if (i < 4) {
                hit(server, roots.get(i), server.overworld().damageSources().lightningBolt(), true, 0);
                hit(server, roots.get(i), server.overworld().damageSources().lightningBolt(), false, 1);
                for (String path : List.of("dragon_fire", "dragon_ice", "dragon_lightning")) {
                    var key = ResourceKey.create(Registries.DAMAGE_TYPE,
                            Identifier.parse("iceandfire:" + path));
                    hit(server, roots.get(i), new DamageSource(damageTypes.getOrThrow(key)), false, .8F);
                }
            } else {
                hit(server, roots.get(i), new DamageSource(damageTypes.getOrThrow(
                        DamageTypes.ARROW)), false, .8F);
            }
        }
        return roots;
    }

    private static void hit(MinecraftServer server, ItemStack root, DamageSource source,
                            boolean completeSet, float coefficient) {
        var subject = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(subject != null, "Missing damage subject");
        var saved = root.copy();
        subject.setItemSlot(EquipmentSlot.LEGS, root);
        if (completeSet) {
            subject.setItemSlot(EquipmentSlot.HEAD, DamageChecks.nativeItem("dragonsteel_lightning_helmet"));
            subject.setItemSlot(EquipmentSlot.CHEST, DamageChecks.nativeItem("dragonsteel_lightning_chestplate"));
            subject.setItemSlot(EquipmentSlot.FEET, DamageChecks.nativeItem("dragonsteel_lightning_boots"));
        }
        observed = subject;
        before = after = -1; events = 0;
        float health = subject.getHealth();
        try { subject.hurtServer(server.overworld(), source, 10); }
        finally { observed = null; }
        require(events == 1 && before > 0 && Math.abs(after - before * coefficient) < .0001F
                        && Math.abs(health - subject.getHealth() - after) < .0001F, "Saved root lost native damage/set contribution: " + source);
        require(ItemStack.matches(saved, subject.getItemBySlot(EquipmentSlot.LEGS)), "Damage changed saved root/history");
    }

    static Path report(String name) { return Path.of(System.getProperty("leggings.test.report"), name); }
}
