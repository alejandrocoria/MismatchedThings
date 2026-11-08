package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import quek.undergarden.registry.UGAttachments;
import quek.undergarden.registry.UGDataComponents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.amount;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.charge;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.player;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.tick;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.PRODUCTS;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.history;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.lower;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.pair;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.recompose;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.sources;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** A real saved chest and an independently encoded oracle, reopened in the next client/server JVM. */
final class RogdoriumLifecycleChecks {
    private static final BlockPos CHEST = new BlockPos(8, 4, 0);
    private static final List<String> CASES = List.of("spent", "refilled", "synthetic-owner", "synthetic-inverse-absent", "zero", "root-only-capacity");

    private RogdoriumLifecycleChecks() {}

    static String seed(MinecraftServer server) throws Exception {
        var level = server.overworld();
        if (PRODUCTS.size() == 2) {
            coexistence(server);
        }
        level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        chest.clearContent();
        var baseline = new CompoundTag();
        baseline.putString("provenance", "Current synthetic roots; OWNER cases reproduce old layout, not a published 1.0.0 save");
        int index = 0;
        for (String product : PRODUCTS) {
            var source = item("undergarden:froststeel_" + lower(product));
            charge(source, 1201, 3600);
            var other = item("minecraft:diamond_" + lower(product));
            var roots = pair(product, source, other);
            var spent = roots.getFirst();
            var wearer = player(level);
            wearer.setItemSlot(product.equals("Boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS, spent);
            tick(wearer);
            require(amount(spent) < 600 && amount(spent) > 0, "Seed was not actually spent natively");
            var refilled = RogdoriumInfuserChecks.refill(level, spent, 2);
            require(history(spent, product).equals(history(refilled, product)), "Native refill changed history");
            var oldOwner = roots.getFirst().copy();
            charge(oldOwner, 1201, 3600);
            var oldInverse = roots.getLast().copy();
            oldInverse.remove(UGDataComponents.ROGDORIUM_INFUSION);
            var zero = roots.getFirst().copy();
            charge(zero, 0, 3600);
            var plain = item("minecraft:iron_" + lower(product));
            var rootOnly = pair(product, plain, plain).getLast();
            charge(rootOnly, 53, 7200); // Persistence only; no promise native spending/refilling preserves custom capacity.
            var samples = List.of(spent, refilled, oldOwner, oldInverse, zero, rootOnly);
            for (int sample = 0; sample < samples.size(); sample++) {
                samples.get(sample).set(DataComponents.CUSTOM_NAME, Component.literal(product + "/" + CASES.get(sample)));
                baseline.put(product + "-" + CASES.get(sample), RecoveryChecks.encode(samples.get(sample), server.registryAccess()));
                chest.setItem(index++, samples.get(sample).copy());
            }
            if (product.equals("Leggings")) {
                ordinary(server);
            }
        }
        baseline.putInt("count", index);
        NbtIo.writeCompressed(baseline, baseline());
        chest.setChanged();
        server.saveEverything(true, true, true);
        return "Saved real spent/refilled roots, synthetic former OWNER/absent-inverse layouts, explicit zero and root-only custom capacity; exact history oracle\n";
    }

    static String reopen(MinecraftServer server) throws Exception {
        var expected = NbtIo.readCompressed(baseline(), NbtAccounter.unlimitedHeap());
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        require(chest != null, "Saved chest was not reopened");
        int index = 0;
        for (String product : PRODUCTS) {
            for (String sample : CASES) {
                var root = chest.getItem(index++);
                var raw = RecoveryChecks.encode(root, server.registryAccess());
                require(raw.equals(expected.getCompoundOrEmpty(product + "-" + sample)), "Saved state/history changed: " + product + "/" + sample);
                var before = root.copy();
                var histories = sources(root, product);
                var rebuilt = recompose(product, root, histories.getFirst(), histories.getLast());
                // Derived repair ingredients can reorder with inverse sources; live state/provenance must not change.
                require(Objects.equals(root.get(UGDataComponents.ROGDORIUM_INFUSION), rebuilt.get(UGDataComponents.ROGDORIUM_INFUSION))
                        && history(root, product).equals(history(rebuilt, product)) && root.getCustomName().equals(rebuilt.getCustomName())
                        && ItemStack.matches(root, before), "Reopened live state/history changed: " + product + "/" + sample);
            }
        }
        require(index == expected.getIntOr("count", -1), "Wrong saved graph");
        return "A second JVM reopened " + index + " actual chest items with exact NBT; recomposition retained all live values/absence/history\n";
    }

    private static Path baseline() {
        return Path.of(System.getProperty("leggings.test.report"), "saved-roots.nbt");
    }

    private static void coexistence(MinecraftServer server) throws Exception {
        var nativeWearer = player(server.overworld());
        var mixedWearer = player(server.overworld());
        var nativeItems = new ArrayList<ItemStack>();
        var mixedItems = new ArrayList<ItemStack>();
        var histories = new ArrayList<Object>();
        for (String product : PRODUCTS) {
            var source = item("minecraft:iron_" + lower(product));
            charge(source, 5, 3600);
            var mixed = pair(product, source, source).getFirst();
            var slot = product.equals("Boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
            nativeWearer.setItemSlot(slot, source);
            mixedWearer.setItemSlot(slot, mixed);
            nativeItems.add(source);
            mixedItems.add(mixed);
            histories.add(history(mixed, product));
        }
        tick(nativeWearer);
        tick(mixedWearer);
        require(Math.abs(nativeWearer.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection()
                - mixedWearer.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection()) < .0001,
                "Joint protection diverged from native armor");
        for (int index = 0; index < PRODUCTS.size(); index++) {
            require(amount(nativeItems.get(index)) < 5 && amount(mixedItems.get(index)) == amount(nativeItems.get(index))
                    && histories.get(index).equals(history(mixedItems.get(index), PRODUCTS.get(index))),
                    "Joint slot spending/history diverged from native armor");
        }
    }

    private static void ordinary(MinecraftServer server) throws Exception {
        var wearer = player(server.overworld());
        for (String family : List.of("cloggrum", "froststeel", "utherium", "ancient")) {
            var source = item("undergarden:" + family + "_leggings");
            var other = item("minecraft:diamond_leggings");
            double nativeArmor = armor(wearer, source);
            double partnerArmor = armor(wearer, other);
            for (var root : pair("Leggings", source, other)) {
                require(Math.abs(armor(wearer, root) - (nativeArmor + partnerArmor) / 2) < .0001, "Ordinary equipped armor " + family);
                double base = wearer.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
                require(Math.abs(wearer.getAttributeValue(Attributes.MOVEMENT_SPEED) / base
                        - (family.equals("froststeel") ? .975 : 1)) < .0001, "Froststeel equipped average " + family);
            }
        }
    }

    private static double armor(FakePlayer wearer, ItemStack stack) {
        wearer.setItemSlot(EquipmentSlot.LEGS, stack);
        ((EquipmentUpdatesInvoker) wearer).leggings$detectEquipmentUpdates();
        return wearer.getAttributeValue(Attributes.ARMOR);
    }
}
