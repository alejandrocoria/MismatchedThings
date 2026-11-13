package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.entities.DecoyEntity;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.AbilityParams;
import com.direwolf20.justdirethings.common.items.interfaces.Helpers;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityBinding;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Disposable orchestration proof. No production adapter, input permission or generic tool identity. */
public final class DecoyCandidate {
    private DecoyCandidate() {}

    public static ItemStack nativeItem(String tier) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:" + tier + "_leggings")));
    }

    static ItemStack view(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (root.getCount() != 1 || data == null) { return null; }
        ItemStack selected = null;
        for (var source : List.of(data.left().source(), data.right().source())) {
            for (String tier : List.of("celestigem", "eclipsealloy")) {
                var nativeItem = nativeItem(tier);
                if (source.getCount() == 1 && source.is(nativeItem.getItem())
                        && ((ToggleableTool) nativeItem.getItem()).getAllAbilities().contains(Ability.DECOY)
                        && (selected == null || tier.equals("eclipsealloy"))) {
                    selected = nativeItem;
                }
            }
        }
        if (selected != null) { selected.applyComponents(root.getComponents()); }
        return selected;
    }

    static AbilityParams parameters(ItemStack root) {
        var view = view(root);
        return view == null ? null : ((ToggleableTool) view.getItem()).getAbilityParams(Ability.DECOY);
    }

    static List<AbilityCooldown> clocks(ItemStack root) {
        List<?> stored = root.getOrDefault(JustDireDataComponents.ABILITY_COOLDOWNS.get(), List.of());
        return stored.stream().map(AbilityCooldown.class::cast).toList();
    }

    static boolean bound(ItemStack root, int key, boolean mouse, boolean equipped) {
        if (view(root) == null || root.getOrDefault(JustDireDataComponents.ABILITY_BINDING_MODES.get(Ability.DECOY), 0) != 2) { return false; }
        List<?> stored = root.getOrDefault(JustDireDataComponents.ABILITY_BINDINGS.get(), List.of());
        return stored.stream().map(AbilityBinding.class::cast).anyMatch(record -> record.abilityName().equals("decoy")
                && record.key() == key && record.isMouse() == mouse && (!record.requireEquipped() || equipped));
    }

    static void importClock(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var params = parameters(root);
        AbilityCooldown selected = null;
        long longest = -1;
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (!source.is(nativeItem("celestigem").getItem()) && !source.is(nativeItem("eclipsealloy").getItem())) { continue; }
            for (var clock : clocks(source)) {
                if (!clock.abilityName().equals("decoy")) { continue; }
                long pending = (long) clock.cooldownTicks() + (clock.isactive() ? params.cooldown : 0);
                if (pending > longest) { selected = clock; longest = pending; }
            }
        }
        var next = new ArrayList<>(clocks(root).stream().filter(clock -> !clock.abilityName().equals("decoy")).toList());
        if (selected != null) { next.add(new AbilityCooldown("decoy", selected.cooldownTicks(), selected.isactive())); }
        if (next.isEmpty()) { root.remove(JustDireDataComponents.ABILITY_COOLDOWNS); }
        else { root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.copyOf(next)); }
    }

    static boolean activate(ServerPlayer player, int slot, ItemStack expected, Predicate<DecoyEntity> spawn, boolean partialDebit) {
        var root = player.getInventory().getItem(slot);
        var before = expected.copy();
        var view = view(root);
        if (root != expected || view == null || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu
                || clocks(root).stream().anyMatch(record -> record.abilityName().equals("decoy"))
                || !((ToggleableTool) view.getItem()).canUseAbilityAndDurability(view, Ability.DECOY)) {
            return false;
        }
        var params = ((ToggleableTool) view.getItem()).getAbilityParams(Ability.DECOY);
        int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING));
        int bill = player.gameMode.getGameModeForPlayer() == GameType.CREATIVE ? 0
                : (int) Math.max(0, Ability.DECOY.getFeCost() - Ability.DECOY.getFeCost() * Math.min(1, unbreaking * .1));
        var wallet = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
        if (wallet == null) { return false; }
        var next = new ArrayList<>(clocks(root));
        next.add(new AbilityCooldown("decoy", params.activeCooldown, true));
        var entity = new DecoyEntity(player.level());
        entity.setPos(player.position());
        entity.setSummonerName(player.getName().getString());
        entity.setOwnerUUID(player.getUUID());
        try (var transaction = Transaction.openRoot()) {
            if (wallet.extract(partialDebit ? Math.max(0, bill - 1) : bill, transaction) != bill
                    || player.getInventory().getItem(slot) != root || !ItemStack.matches(root, before)) { return false; }
            if (!spawn.test(entity)) { return false; }
            if (player.getInventory().getItem(slot) != root || !ItemStack.matches(root, before)) {
                entity.discard();
                return false;
            }
            // A cancelled spawn never commits a debit. No failure-swallowing/retry crosses commit.
            transaction.commit();
        }
        root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.copyOf(next));
        Helpers.playSoundToAll(player, SoundEvents.EVOKER_PREPARE_SUMMON, 1, 1);
        return true;
    }

    static void tick(ServerPlayer player) {
        var root = player.getItemBySlot(EquipmentSlot.LEGS);
        var params = parameters(root);
        if (params == null) { return; }
        var records = clocks(root);
        var decoys = records.stream().filter(record -> record.abilityName().equals("decoy")).toList();
        if (decoys.size() != 1 || decoys.getFirst().cooldownTicks() <= 0) { return; }
        var next = new ArrayList<AbilityCooldown>();
        SoundEvent sound = null;
        for (var record : records) {
            if (!record.abilityName().equals("decoy")) { next.add(record); }
            else if (record.cooldownTicks() > 1) { next.add(new AbilityCooldown("decoy", record.cooldownTicks() - 1, record.isactive())); }
            else if (record.isactive()) {
                next.add(new AbilityCooldown("decoy", params.cooldown, false));
                sound = SoundEvents.CONDUIT_DEACTIVATE;
            } else { sound = SoundEvents.ENDER_EYE_DEATH; }
        }
        if (next.isEmpty()) { root.remove(JustDireDataComponents.ABILITY_COOLDOWNS); }
        else { root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.copyOf(next)); }
        if (sound != null) { Helpers.playSoundToAll(player, sound, 1, 1); }
    }

    public static ItemStack hud(ItemStack root, EquipmentSlot slot) {
        var view = slot == EquipmentSlot.LEGS ? view(root) : null;
        if (view == null) { return root; }
        var params = parameters(root);
        var records = clocks(root).stream()
                .filter(record -> record.abilityName().equals("decoy")).toList();
        if (records.size() != 1 || records.getFirst().cooldownTicks() <= 0) {
            view.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
        } else {
            var clock = records.getFirst();
            int duration = clock.isactive() ? params.activeCooldown : params.cooldown;
            view.set(JustDireDataComponents.ABILITY_COOLDOWNS,
                    List.of(new AbilityCooldown("decoy", Math.min(duration, clock.cooldownTicks()), clock.isactive())));
        }
        return view;
    }
}
