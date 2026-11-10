package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Saved paid roots, real dedicated player ticks and ordinary S2C effects; no candidate dispatcher. */
final class WearerLifecycle {
    static final List<String> MATERIALS = List.of("nickel", "lead", "crimson_steel", "refined_iron");
    static final List<String> FAMILIES = List.of("adamant", "aquatic", "flame_ward", "stellar");
    static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
    private static int step;
    private static int started;
    private static int rejoinedAt = -1;
    private static CompoundTag frozen;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    static boolean enabled() { return Boolean.getBoolean("leggings.test.sgWearerLifecycle"); }
    private static Path baseline(String name) { return Path.of(System.getProperty("leggings.test.sgStateBaseline"), name); }

    static void seed(ServerPlayer player, BarrelBlockEntity barrel, CompoundTag saved) throws Exception {
        for (int family = 0; family < 4; family++) {
            var source = FoundationChecks.armor(MATERIALS.get(family));
            var other = family == 3 ? FoundationChecks.armor("gold") : new ItemStack(Items.DIAMOND_LEGGINGS);
            var roots = FoundationChecks.paid(player, source, other);
            for (int side = 0; side < 2; side++) {
                var root = roots.get(side);
                root.setDamageValue(17);
                root = StateChecks.workstations(player, root, family * 2 + side);
                // Freeze incidental self repair only while witnessing full ordinary item packets.
                root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                store(barrel, saved, 6 + family * 2 + side, root, player);
            }
            for (int slot = 1; slot < 4; slot++) {
                var nativePiece = WielderChecks.armor(List.of("leggings", "boots", "helmet", "chestplate").get(slot),
                        List.of("legging_plates", "boot_plates", "helmet_plates", "chestplate_plates").get(slot), MATERIALS.get(family));
                if (slot == 1 && Boolean.getBoolean("leggings.test.boots")) {
                    var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                    var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativePiece, new ItemStack(Items.DIAMOND_BOOTS));
                    nativePiece = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
                }
                nativePiece.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                store(barrel, saved, 14 + family * 3 + slot - 1, nativePiece, player);
            }
        }
        ManualFixtures.wearer(player);
    }

    private static void store(BarrelBlockEntity barrel, CompoundTag saved, int slot, ItemStack root, ServerPlayer player) {
        barrel.setItem(slot, root);
        saved.put("root" + slot, StateChecks.encode(root, player.registryAccess()));
        barrel.setChanged();
    }

    static void reopen(ServerPlayer player, BarrelBlockEntity barrel, CompoundTag expected) throws Exception {
        for (int slot = 6; slot < 26; slot++) {
            require(StateChecks.encode(barrel.getItem(slot), player.registryAccess()).equals(expected.get("root" + slot)),
                    "Wearer saved root/source/state/trim changed " + slot);
        }
        for (int slot : List.of(12, 13)) {
            var root = barrel.getItem(slot).copy();
            var source = MixedLeggingsStructure.validData(root).orElseThrow();
            var trait = TraitHelper.getTraits(source.left().source()).stream().filter(t -> t.getTraitId().getPath().equals("stellar"))
                    .findFirst().orElseGet(() -> TraitHelper.getTraits(source.right().source()).stream()
                            .filter(t -> t.getTraitId().getPath().equals("stellar")).findFirst().orElseThrow());
            var repair = trait.getTrait().getEffects().stream().filter(e -> e.getClass().getSimpleName().equals("SelfRepairTraitEffect")).findFirst().orElseThrow();
            var chance = repair.getClass().getDeclaredField("activationChance"); chance.setAccessible(true);
            var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM"); field.setAccessible(true);
            var random = (Random) field.get(null);
            for (boolean activation : List.of(true, false)) {
                long seed = 0;
                while ((new Random(seed).nextDouble() < chance.getFloat(repair) * trait.getLevel()) != activation && seed < 100_000) { seed++; }
                require(seed < 100_000, "Missing bounded reopened Stellar repair seed");
                root.remove(DataComponents.UNBREAKABLE);
                root.setDamageValue(12);
                var before = root.copy();
                var oracle = new Random(seed); oracle.nextDouble();
                random.setSeed(seed);
                player.tickCount = 20;
                root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                require(root.getDamageValue() == 12 - (activation ? trait.getLevel() : 0)
                        && random.nextDouble() == oracle.nextDouble(), "Reopened Stellar duplicated repair roll/heal");
                before.setDamageValue(root.getDamageValue());
                require(ItemStack.matches(before, root), "Reopened wearer changed non-repair state/history");
                require(root.getItem().makesPiglinsNeutral(root, player), "Saved Brilliant orientation lost recognition");
            }
        }
    }

    static boolean rejoined(ServerPlayer player) throws Exception {
        if (rejoinedAt < 0) { rejoinedAt = player.tickCount; }
        require(snapshot(player).equals(StateChecks.read(baseline("wearer-player.nbt"))), "Rejoined player ticks changed saved roots before setup");
        if (player.tickCount - rejoinedAt < 60) { return false; }
        checkEffects(player, 3, count(5), true);
        if (!Files.exists(StateChecks.report("wearer-rejoin.settled"))) {
            NbtIo.writeCompressed(snapshot(player), StateChecks.report("wearer-rejoin.nbt"));
            Files.writeString(StateChecks.report("wearer-rejoin.settled"), "Saved worn roots refreshed through 60 actual player ticks before setup\n");
        }
        return Files.exists(StateChecks.report("wearer-rejoin.ok"));
    }

    static void start(ServerPlayer player, BarrelBlockEntity barrel) throws Exception {
        step = 0;
        prepare(player, barrel);
    }

    static boolean tick(ServerPlayer player, BarrelBlockEntity barrel) throws Exception {
        if (step == 12) {
            if (reload == null) { reload = player.level().getServer().reloadResources(player.level().getServer().getPackRepository().getSelectedIds()); }
            if (!reload.isDone()) { return false; }
            reload.join();
            Files.writeString(StateChecks.report("wearer-reload.ok"), "Complete server resource reload with retained paid roots/native effects\n");
        }
        require(snapshot(player).equals(frozen), "Ordinary wearer ticks changed paid root/source/state/trim");
        if (player.tickCount - started < 60) { return false; }
        int family = step / 6, mode = step % 6;
        checkEffects(player, family, count(mode), mode != 4);
        require(player.hasEffect(MobEffects.LUCK), "Moving gear erased unrelated potion");
        Files.writeString(StateChecks.report("wearer-" + step + ".settled"), "60 actual dedicated player ticks; refresh/expiry accepted\n");
        if (!Files.exists(StateChecks.report("wearer-" + step + ".ok"))) { return false; }
        if (++step == 24) {
            NbtIo.writeCompressed(snapshot(player), baseline("wearer-player.nbt"));
            Files.writeString(StateChecks.report("wearer.txt"), "Four native controls and paid inverses; full sets, selected hands, idle natural expiry, mixed-only slots; real TCP potions/speed/jump, no root writes; saved Stellar success/nonactivation each consumes one repair roll\n");
            return true;
        }
        prepare(player, barrel);
        return false;
    }

    static int count(int mode) { return mode == 3 ? 3 : mode == 4 ? 0 : mode == 5 ? Boolean.getBoolean("leggings.test.boots") ? 2 : 1 : 4; }

    private static void prepare(ServerPlayer player, BarrelBlockEntity barrel) throws Exception {
        int family = step / 6, mode = step % 6;
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        // Deliberately keep preceding native effects: idle acceptance waits for natural expiry.
        if (!player.hasEffect(MobEffects.LUCK)) { player.addEffect(new MobEffectInstance(MobEffects.LUCK, 10000)); }
        var root = mode == 0 ? FoundationChecks.armor(MATERIALS.get(family)) : barrel.getItem(6 + family * 2 + (mode == 2 ? 1 : 0)).copy();
        if (mode == 0) { root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
        if (mode == 3) { player.setItemSlot(EquipmentSlot.OFFHAND, root); }
        else if (mode == 4) { player.getInventory().setItem(1, root); }
        else { player.setItemSlot(EquipmentSlot.LEGS, root); }
        if (mode != 4) {
            for (int slot = 1; slot < (mode == 5 ? Boolean.getBoolean("leggings.test.boots") ? 2 : 1 : 4); slot++) {
                var piece = barrel.getItem(14 + family * 3 + slot - 1).copy();
                // The native control is independent of the mixed FEET contribution.
                if (mode == 0 && slot == 1) { piece = WielderChecks.armor("boots", "boot_plates", MATERIALS.get(family)); }
                player.setItemSlot(ARMOR.get(slot), piece);
            }
        }
        started = player.tickCount;
        frozen = snapshot(player);
        player.inventoryMenu.broadcastChanges();
        NbtIo.writeCompressed(frozen, StateChecks.report("wearer-" + step + ".nbt"));
        Files.writeString(StateChecks.report("wearer-" + step + ".ready"), FAMILIES.get(family) + "/" + mode + "\n");
    }

    static CompoundTag snapshot(Player player) {
        var tag = new CompoundTag();
        for (var slot : EquipmentSlot.values()) { tag.put(slot.getSerializedName(), StateChecks.encode(player.getItemBySlot(slot), player.registryAccess())); }
        tag.put("idle", StateChecks.encode(player.getInventory().getItem(1), player.registryAccess()));
        return tag;
    }

    static void checkEffects(Player player, int family, int count, boolean selected) {
        require(effectsMatch(player, family, count, selected), "Connected native potions differ: family=" + family
                + "; count=" + count + "; selected=" + selected + "; effects=" + player.getActiveEffects());
    }

    static boolean effectsMatch(Player player, int family, int count, boolean selected) {
        return matches(player, MobEffects.RESISTANCE, family == 0 && selected ? count == 4 ? 2 : 1 : 0)
                && matches(player, MobEffects.WATER_BREATHING, family == 1 && selected && count == 4 ? 1 : 0)
                && matches(player, MobEffects.FIRE_RESISTANCE, family == 2 && selected && count == 4 ? 1 : 0)
                && matches(player, MobEffects.SPEED, family == 3 && selected ? Math.max(0, count - 1) : 0)
                && matches(player, MobEffects.JUMP_BOOST, family == 3 && selected ? Math.max(1, count) : 0);
    }

    private static boolean matches(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type, int level) {
        var effect = player.getEffect(type);
        return level == 0 ? effect == null : effect != null && effect.getAmplifier() == level - 1
                && effect.getDuration() > 0 && effect.getDuration() <= 38 && effect.isAmbient() && !effect.isVisible();
    }
}
