package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Bounded dedicated-server gameplay. Files acknowledge receipt; all gameplay travels through ordinary TCP packets. */
public final class DefenseLifecycle {
    static final int SAMPLES = 3;
    private static int ticks;
    private static int sample;
    private static int phase;
    private static int started;
    private static boolean finished;
    private static ItemStack legs = ItemStack.EMPTY;
    private static ItemStack feet = ItemStack.EMPTY;
    private static ItemStack beforeLegs;
    private static ItemStack beforeFeet;
    private static Zombie attacker;
    private static LivingFallEvent fall;
    private static int sounds;
    private static int bills;
    private static int requested;
    private static boolean watching;
    private static double armorBase;
    private static double toughnessBase;
    private static double minimumY;
    private static double rise;
    private static double nativeRise;
    private static final StringBuilder REPORT = new StringBuilder();

    private DefenseLifecycle() {}

    public static void wear(ItemStack stack, int amount, EquipmentSlot slot) {
        if (!watching) { return; }
        require(stack != legs, "Connected fall billed LEGS");
        if (stack == feet) {
            require(slot == EquipmentSlot.FEET, "Connected fall billed wrong slot");
            bills++;
            requested += amount;
        }
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "Connected defense timeout sample=" + sample + " phase=" + phase);
            require(!Files.exists(StateChecks.report("client.failure.txt")), "Connected defense client failed");
            if (ticks == 20) {
                require(ModList.get().isLoaded("silentgear") && ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.boots"),
                        "Wrong connected defensive graph");
                server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                for (int x = 1; x < 18; x++) {
                    for (int z = 1; z < 18; z++) {
                        server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
                listen();
                Files.writeString(StateChecks.report("ready.txt"), "Dedicated defensive server ready\n");
            }
            if (sample == SAMPLES) {
                if (Files.exists(StateChecks.report("client.ok")) && server.getPlayerList().getPlayers().isEmpty()) {
                    Files.writeString(StateChecks.report("defense-server.txt"), REPORT);
                    Files.writeString(StateChecks.report("state.txt"), REPORT);
                    SkeletonChecks.passed("server");
                    finished = true;
                    server.halt(false);
                }
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(StateChecks.report("defense-client-ready.ok"))) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            if (phase == 0) {
                prepare(player);
                phase = 1;
                started = ticks;
            }
            require(ticks - started < 600, "Connected sample stalled: " + sample + "/" + phase + " y=" + player.getY()
                    + " fall=" + (fall == null ? "none" : fall.getDistance() + "/canceled=" + fall.isCanceled()) + " sounds=" + sounds + " bills=" + bills);
            if (phase <= 3) {
                if (!ack("health-" + (phase - 1))) { return; }
                if (phase == 1) {
                    // Equipment attributes settle during ordinary ticks, after the initial setItemSlot call.
                    player.getAttribute(Attributes.ARMOR).setBaseValue(armorBase - player.getAttributeValue(Attributes.ARMOR));
                    player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(toughnessBase - player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                    close(player.getAttributeValue(Attributes.ARMOR), 0, "Connected armor control");
                    close(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0, "Connected toughness control");
                }
                player.setHealth(player.getMaxHealth());
                player.invulnerableTime = 0;
                if (phase < 3) {
                    require(player.hurtServer(player.level(), phase == 1 ? player.damageSources().lava() : player.damageSources().flyIntoWall(), 10),
                            "Connected incoming hit rejected");
                    close(player.getHealth(), player.getMaxHealth() - (phase == 1 ? 8.4 : 8), "Native incoming health");
                } else {
                    // No goals, but ordinary travel remains enabled so native retaliation produces real entity motion packets.
                    attacker = new Zombie(EntityType.ZOMBIE, player.level()) {
                        @Override protected void registerGoals() {}
                    };
                    attacker.setNoGravity(true);
                    attacker.setInvulnerable(true);
                    attacker.setPos(10.5, 83, 8.5);
                    attacker.setYRot(90);
                    attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
                    require(player.level().addFreshEntity(attacker) && attacker.doHurtTarget(player.level(), player), "Real melee hit rejected");
                    close(attacker.getDeltaMovement().x, 2, "Native retaliation strength");
                    close(player.getHealth(), player.getMaxHealth() - 4, "Real melee health");
                }
                require(ItemStack.matches(beforeLegs, legs), "Protected incoming root/source changed");
                publish(player, "health-" + phase);
                phase++;
            } else if (phase == 4 && ack("health-3")) {
                attacker.discard();
                attacker = null;
                player.getAttribute(Attributes.ARMOR).setBaseValue(armorBase);
                player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(toughnessBase);
                legs.remove(DataComponents.UNBREAKABLE);
                beforeLegs = legs.copy();
                player.setItemSlot(EquipmentSlot.FEET, feet);
                beforeFeet = feet.copy();
                player.setHealth(player.getMaxHealth());
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0;
                player.connection.teleport(8.5, 95, 8.5, 0, 0);
                fall = null;
                bills = requested = sounds = 0;
                minimumY = Double.MAX_VALUE;
                rise = 0;
                publish(player, "fall");
                phase = 5;
            } else if (phase == 5 && ack("fall")) {
                watching = true;
                player.setNoGravity(false);
                phase = 6;
            } else if (phase == 6) {
                minimumY = Math.min(minimumY, player.getY());
                if (minimumY <= 81.05) { rise = Math.max(rise, player.getY() - 81); }
                if (!ack("landing")) { return; }
                if (sample == 0) {
                    nativeRise = rise;
                    close(nativeRise, 0, "Pinned native dedicated-server rebound limitation");
                }
                close(rise, nativeRise, "Mixed/native dedicated-server motion parity");
                watching = false;
                require(fall != null && fall.isCanceled() && sounds == 1, "Native connected fall/sound not observed exactly once");
                int bill = (int) (fall.getDistance() / 3) - 1;
                require(bill > 0 && bills == 1 && requested == bill && feet.getDamageValue() - beforeFeet.getDamageValue() == bill,
                        "Connected FEET did not pay exactly one ordinary fall bill");
                require(ItemStack.matches(beforeLegs, legs), "Connected fall changed LEGS/source history");
                var expectedFeet = beforeFeet.copy();
                expectedFeet.setDamageValue(feet.getDamageValue());
                require(ItemStack.matches(expectedFeet, feet), "Connected fall changed FEET source history/components beyond wear");
                close(player.getHealth(), player.getMaxHealth(), "Connected fall health");
                player.setNoGravity(true);
                publish(player, "used");
                phase = 7;
            } else if (phase == 7 && ack("used")) {
                REPORT.append("sample ").append(sample).append(": native Heat IV/Bounce I health, real melee retaliation, dedicated-server rise=")
                        .append(rise).append(" (native parity, no movement workaround), one native sound, one FEET bill=")
                        .append(requested).append(", LEGS and source histories unchanged\n");
                sample++;
                phase = 0;
                if (sample == SAMPLES) { Files.writeString(StateChecks.report("finish.txt"), "Connected defensive samples passed\n"); }
            }
        } catch (Throwable failure) {
            watching = false;
            if (attacker != null) { attacker.discard(); }
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void prepare(ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        DefenseIncomingChecks.clear(player);
        player.removeAllEffects();
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.setNoGravity(true);
        player.getFoodData().setFoodLevel(10);
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0);
        player.connection.teleport(8.5, 83, 8.5, 0, 0);
        var source = FoundationChecks.armor("crimson_iron", "lining", "slime");
        legs = sample == 0 ? source : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).get(sample - 1);
        feet = new ItemStack(Items.DIAMOND_BOOTS);
        if (sample > 0 && Boolean.getBoolean("leggings.test.boots")) {
            var bootSource = sample == 1 ? new ItemStack(Items.IRON_BOOTS)
                    : DefenseIncomingChecks.only(DefenseIncomingChecks.nativePiece("boots", "boot_plates", "iron"), DefenseIncomingChecks.trait("bounce", 1));
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, bootSource, feet);
            feet = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        }
        // Isolate incoming effects from numeric armor and ordinary hit wear. Fall checks below remove Unbreakable again.
        legs.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        beforeLegs = legs.copy();
        armorBase = player.getAttribute(Attributes.ARMOR).getBaseValue();
        toughnessBase = player.getAttribute(Attributes.ARMOR_TOUGHNESS).getBaseValue();
        publish(player, "health-0");
    }

    private static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingFallEvent event) -> {
            if (watching && event.getEntity() instanceof ServerPlayer && event.getDistance() > 3) { fall = event; }
        });
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (watching && !event.getLevel().isClientSide() && event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Native server sound parameters changed");
                sounds++;
            }
        });
    }

    private static boolean ack(String name) { return Files.exists(StateChecks.report("defense-" + sample + "." + name + ".ok")); }

    private static void publish(ServerPlayer player, String name) throws Exception {
        player.inventoryMenu.broadcastChanges();
        var json = new JsonObject();
        var ops = player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        json.add("legs", ItemStack.CODEC.encodeStart(ops, player.getItemBySlot(EquipmentSlot.LEGS)).getOrThrow());
        json.add("feet", ItemStack.OPTIONAL_CODEC.encodeStart(ops, player.getItemBySlot(EquipmentSlot.FEET)).getOrThrow());
        json.addProperty("health", player.getHealth());
        if (attacker != null) { json.addProperty("attacker", attacker.getId()); }
        Files.writeString(StateChecks.report("defense-" + sample + "." + name + ".json"), json.toString());
        Files.writeString(StateChecks.report("defense-" + sample + "." + name + ".ready"), "Complete server observation published\n");
    }

    private static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < 1E-4, message + ": " + actual + " != " + expected); }
}
