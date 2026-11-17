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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native new-upgrade positives compared with all nine native/mixed feet/legs combinations. */
final class NeoVitaeUpdateSentient {
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final StringBuilder report;

    NeoVitaeUpdateSentient(MinecraftServer server, ServerPlayer player, StringBuilder report) {
        this.server = server;
        this.player = player;
        this.report = report;
    }

    void run() throws Exception {
        if (!server.overworld().players().contains(player)) { server.overworld().addNewPlayer(player); }
        player.setGameMode(GameType.SURVIVAL);
        var boots = new ArrayList<ItemStack>();
        boots.add(NeoVitaeUpdateSpike.nativePiece(EquipmentSlot.FEET));
        boots.addAll(NeoVitaeUpdateSpike.pair(boots.getFirst(), new ItemStack(Items.DIAMOND_BOOTS), EquipmentSlot.FEET));
        var legs = new ArrayList<ItemStack>();
        legs.add(NeoVitaeUpdateSpike.nativePiece(EquipmentSlot.LEGS));
        legs.addAll(NeoVitaeUpdateSpike.pair(legs.getFirst(), new ItemStack(Items.DIAMOND_LEGGINGS), EquipmentSlot.LEGS));
        for (String upgrade : List.of("aquatic_agility", "gills", "lava_strider", "unbounded_flight", "deus_sanguinis")) {
            var holder = server.registryAccess().lookupOrThrow(NVRegistries.Keys.SENTIENT_UPGRADES)
                    .get(Identifier.fromNamespaceAndPath("neovitae", upgrade)).orElseThrow();
            var nativeResult = measure(holder, boots.getFirst(), legs.getFirst(), true);
            require(switch (upgrade) {
                case "aquatic_agility" -> nativeResult.swim > 1;
                case "gills" -> nativeResult.air > 100;
                case "lava_strider" -> nativeResult.lava;
                case "unbounded_flight" -> nativeResult.flight > 0 && nativeResult.flightEV == 5;
                case "deus_sanguinis" -> nativeResult.siphon > 0 && nativeResult.shield > 0;
                default -> false;
            }, "Native new-upgrade control is not positive: " + upgrade + ' ' + nativeResult);
            for (var feet : boots) {
                for (var leggings : legs) {
                    require(nativeResult.equals(measure(holder, feet, leggings, true)), "Mixed new-upgrade behavior differs: " + upgrade);
                }
            }
            var negative = measure(holder, new ItemStack(Items.IRON_BOOTS), legs.getLast(), false);
            require(negative.swim == 1 && negative.siphon == 0 && negative.shield == 0
                    && negative.flight == 0 && negative.flightEV == 0 && negative.air < 100 && !negative.lava,
                    "Missing-set native control retained an upgrade: " + negative);
            report.append("Sentient ").append(upgrade).append(": ").append(nativeResult)
                    .append("; native positive equals all nine original/mixed feet/legs configurations; missing-set negative\n");
        }
        // Training remains a chestplate operation, not a write into either mixed source.
        var holder = server.registryAccess().lookupOrThrow(NVRegistries.Keys.SENTIENT_UPGRADES)
                .get(Identifier.parse("neovitae:aquatic_agility")).orElseThrow();
        measure(holder, boots.getLast(), legs.getLast(), true);
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        float before = chest.get(NVDataComponents.UPGRADES.get()).upgrades().getFloat(holder);
        require(SentientHelper.applyExp(player, holder, 1, true) > 0
                && chest.get(NVDataComponents.UPGRADES.get()).upgrades().getFloat(holder) > before,
                "Native chest-owned progression did not accept experience with both mixed slots");
        report.append("Native representative progression: Aquatic Agility XP increases on the chestplate with both mixed slots; no root upgrade-state copy\n");
    }

    private Measurement measure(Holder<SentientUpgrade> upgrade, ItemStack feet, ItemStack legs, boolean complete) throws Exception {
        NeoVitaeUpdateSpike.clear(player);
        player.setPos(8.5, 81, 8.5);
        var chest = NeoVitaeUpdateSpike.item("sentient_plate");
        chest.set(NVDataComponents.CURRENT_MAX_UPGRADE_POINTS.get(), 600);
        HellforgedHelper.apply(chest, server.registryAccess());
        var map = new Object2FloatOpenHashMap<Holder<SentientUpgrade>>();
        map.put(upgrade, (float) SentientHelper.getExpForLevel(upgrade, 1));
        chest.set(NVDataComponents.UPGRADES.get(), new SentientStats(map));
        chest.set(NVDataComponents.CURRENT_UPGRADE_POINTS.get(), 0);
        player.setItemSlot(EquipmentSlot.HEAD, NeoVitaeUpdateSpike.item("sentient_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.FEET, feet.copy());
        player.setItemSlot(EquipmentSlot.LEGS, legs.copy());
        require(SentientHelper.hasFullSet(player) == complete && SentientHelper.meetsEvolutionRequirement(chest, upgrade),
                "Wrong native set/evolution fixture");
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
            require(!player.getAbilities().flying, "Native empty-EV flight did not stop");
        }

        player.setShiftKeyDown(false);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        var canStand = LivingEntity.class.getDeclaredMethod("canStandOnFluid", FluidState.class);
        canStand.setAccessible(true);
        boolean lava = (boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState());
        player.setShiftKeyDown(true);
        require(!(boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState()), "Sneaking did not disable native lava walking");
        player.setShiftKeyDown(false);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        require(!(boolean) canStand.invoke(player, Blocks.LAVA.defaultBlockState().getFluidState()), "Item fireproofing substituted for native Fire Resistance requirement");

        var eye = player.blockPosition().above();
        server.overworld().setBlockAndUpdate(eye.below(), Blocks.WATER.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye, Blocks.WATER.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye.above(), Blocks.WATER.defaultBlockState());
        player.baseTick();
        player.baseTick();
        require(player.isUnderWater(), "Native Gills positive is not actually underwater");
        var decrease = LivingEntity.class.getDeclaredMethod("decreaseAirSupply", int.class);
        decrease.setAccessible(true);
        int air = (int) decrease.invoke(player, 100);
        server.overworld().setBlockAndUpdate(eye.below(), Blocks.AIR.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye, Blocks.AIR.defaultBlockState());
        server.overworld().setBlockAndUpdate(eye.above(), Blocks.AIR.defaultBlockState());
        player.baseTick();
        return new Measurement(swim, siphon, shield, flight, flightEV, air, lava);
    }

    private static Holder<Attribute> attribute(String name) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(name)).orElseThrow();
    }

    private record Measurement(double swim, double siphon, double shield, double flight, int flightEV, int air, boolean lava) {}
}
