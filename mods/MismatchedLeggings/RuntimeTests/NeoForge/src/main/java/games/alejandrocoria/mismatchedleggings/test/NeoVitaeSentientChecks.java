package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.api.soul.AnimaTicket;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.datacomponent.SentientStats;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.registry.NVRegistries;
import com.breakinblocks.neovitae.common.sentient.SentientEventHandler;
import com.breakinblocks.neovitae.common.sentient.SentientHelper;
import com.breakinblocks.neovitae.common.sentient.SentientUpgrade;
import com.breakinblocks.neovitae.util.helper.AnimaHelper;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual level-one effects and one XP operation; upgrade state remains native and chestplate-owned. */
final class NeoVitaeSentientChecks {
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final StringBuilder report;

    NeoVitaeSentientChecks(MinecraftServer server, ServerPlayer player, StringBuilder report) {
        this.server = server;
        this.player = player;
        this.report = report;
    }

    void run() throws Exception {
        var boots = new ArrayList<ItemStack>();
        boots.add(NeoVitaeChecks.item("sentient_boots"));
        if (ModList.get().isLoaded("mismatchedboots")) {
            boots.addAll(NeoVitaeHellforgedChecks.pair(boots.getFirst(), new ItemStack(Items.DIAMOND_BOOTS), EquipmentSlot.FEET));
        }
        var legs = new ArrayList<ItemStack>();
        legs.add(NeoVitaeChecks.item("sentient_leggings"));
        legs.addAll(NeoVitaeHellforgedChecks.pair(legs.getFirst(), new ItemStack(Items.DIAMOND_LEGGINGS), EquipmentSlot.LEGS));
        for (String name : List.of("aquatic_agility", "gills", "lava_strider", "unbounded_flight", "deus_sanguinis")) {
            var upgrade = server.registryAccess().lookupOrThrow(NVRegistries.Keys.SENTIENT_UPGRADES)
                    .get(Identifier.fromNamespaceAndPath("neovitae", name)).orElseThrow();
            var expected = measure(upgrade, boots.getFirst(), legs.getFirst(), true);
            require(switch (name) {
                case "aquatic_agility" -> Math.abs(expected.swim() - 1.2) < 1E-6;
                case "gills" -> expected.air() == 104;
                case "lava_strider" -> expected.lava();
                case "unbounded_flight" -> expected.flight() == 1 && expected.flightEV() == 5;
                case "deus_sanguinis" -> expected.siphon() == 10 && expected.shield() == 10;
                default -> false;
            }, "Inactive native level-one control: " + name + '/' + expected);
            for (var feet : boots) {
                for (var leggings : legs) {
                    require(expected.equals(measure(upgrade, feet, leggings, true)), "Mixed wearer effect differs: " + name);
                }
            }
            var negative = measure(upgrade, new ItemStack(Items.IRON_BOOTS), legs.getLast(), false);
            require(negative.swim() == 1 && negative.siphon() == 0 && negative.shield() == 0
                    && negative.flight() == 0 && negative.flightEV() == 0 && negative.air() == 99 && !negative.lava(),
                    "Missing-set control retained an effect: " + name + '/' + negative);
            report.append(name).append(": ").append(expected).append("; native positive equals ")
                    .append(boots.size() * legs.size()).append(" original/mixed feet/legs configurations; missing-set negative\n");
        }
        var agility = server.registryAccess().lookupOrThrow(NVRegistries.Keys.SENTIENT_UPGRADES)
                .get(Identifier.parse("neovitae:aquatic_agility")).orElseThrow();
        measure(agility, boots.getLast(), legs.getLast(), true);
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float before = chest.get(NVDataComponents.UPGRADES.get()).upgrades().getFloat(agility);
        require(SentientHelper.applyExp(player, agility, 1, true) > 0
                && chest.get(NVDataComponents.UPGRADES.get()).upgrades().getFloat(agility) > before,
                "Chestplate XP progression failed with mixed equipment");
        report.append("Representative Aquatic Agility XP increases on the native chestplate; wearer/source state is not an upgrade store\n");
        clear(player);
    }

    private Measurement measure(Holder<SentientUpgrade> upgrade, ItemStack feet, ItemStack legs, boolean complete) throws Exception {
        clear(player);
        player.setPos(8.5, 81, 8.5);
        var chest = NeoVitaeChecks.item("sentient_plate");
        chest.set(NVDataComponents.CURRENT_MAX_UPGRADE_POINTS.get(), 600);
        HellforgedHelper.apply(chest, server.registryAccess());
        var upgrades = new Object2FloatOpenHashMap<Holder<SentientUpgrade>>();
        upgrades.put(upgrade, (float) SentientHelper.getExpForLevel(upgrade, 1));
        chest.set(NVDataComponents.UPGRADES.get(), new SentientStats(upgrades));
        chest.set(NVDataComponents.CURRENT_UPGRADE_POINTS.get(), 0);
        player.setItemSlot(EquipmentSlot.HEAD, NeoVitaeChecks.item("sentient_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.FEET, feet.copy());
        player.setItemSlot(EquipmentSlot.LEGS, legs.copy());
        require(SentientHelper.hasFullSet(player) == complete && SentientHelper.meetsEvolutionRequirement(chest, upgrade),
                "Wrong native set/evolution prerequisites");
        SentientEventHandler.onEquipmentChange(new LivingEquipmentChangeEvent(player, EquipmentSlot.FEET, ItemStack.EMPTY, feet));
        var detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        detect.setAccessible(true);
        detect.invoke(player);

        double swim = player.getAttributeValue(attribute("neoforge:swim_speed"));
        double siphon = player.getAttributeValue(attribute("neovitae:blood_siphon"));
        double shield = player.getAttributeValue(attribute("neovitae:blood_shield"));
        double flight = player.getAttributeValue(attribute("neoforge:creative_flight"));
        var network = AnimaHelper.getAnima(player);
        network.set(AnimaTicket.create(100), 1000);
        player.setOnGround(false);
        player.getAbilities().flying = true;
        var tickFlight = SentientEventHandler.class.getDeclaredMethod("tickUnboundedFlight", net.minecraft.world.entity.player.Player.class);
        tickFlight.setAccessible(true);
        tickFlight.invoke(null, player);
        int flightEV = 100 - network.getCurrentEV();
        if (flight > 0) {
            network.set(AnimaTicket.create(0), 1000);
            tickFlight.invoke(null, player);
            require(!player.getAbilities().flying, "Flight did not stop at empty EV");
        }

        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        var canStand = LivingEntity.class.getDeclaredMethod("canStandOnFluid", FluidState.class);
        canStand.setAccessible(true);
        boolean lava = (boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState());
        player.setShiftKeyDown(true);
        require(!(boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState()), "Sneaking did not release lava walking");
        player.setShiftKeyDown(false);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        require(!(boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState()), "Item fireproofing substituted for Fire Resistance potion");

        var eye = player.blockPosition().above();
        server.overworld().setBlockAndUpdate(eye.below(), Blocks.WATER.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye, Blocks.WATER.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye.above(), Blocks.WATER.defaultBlockState());
        player.baseTick();
        player.baseTick();
        require(player.isUnderWater(), "Gills fixture was not actually submerged");
        var decrease = LivingEntity.class.getDeclaredMethod("decreaseAirSupply", int.class);
        decrease.setAccessible(true);
        int air = (int) decrease.invoke(player, 100);
        server.overworld().setBlockAndUpdate(eye.below(), Blocks.AIR.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye, Blocks.AIR.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye.above(), Blocks.AIR.defaultBlockState());
        player.baseTick();
        require(ItemStack.matches(feet, player.getItemBySlot(EquipmentSlot.FEET))
                && ItemStack.matches(legs, player.getItemBySlot(EquipmentSlot.LEGS)), "Wearer checks wrote upgrade state into armor/sources");
        return new Measurement(swim, siphon, shield, flight, flightEV, air, lava);
    }

    static void clear(ServerPlayer player) {
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.removeAllEffects();
        player.setShiftKeyDown(false);
        player.getAbilities().flying = false;
    }

    private static Holder<Attribute> attribute(String name) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(name)).orElseThrow();
    }

    private record Measurement(double swim, double siphon, double shield, double flight, int flightEV, int air, boolean lava) {
    }
}
