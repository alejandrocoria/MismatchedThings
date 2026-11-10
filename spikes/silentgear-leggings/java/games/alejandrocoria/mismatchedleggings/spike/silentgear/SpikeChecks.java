package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpikeChecks {
    private static final StringBuilder REPORT = new StringBuilder();
    private static boolean finished;

    private SpikeChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            WearerContracts.run(server, REPORT);
            sets(server);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "silentgear.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            try { Files.writeString(Path.of(System.getProperty("leggings.test.report"), "silentgear.txt"), REPORT); }
            catch (java.io.IOException reportFailure) { failure.addSuppressed(reportFailure); }
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void sets(MinecraftServer server) throws Exception {
        for (var family : WearerContracts.FAMILIES) {
            var source = armor("leggings", "legging_plates", family[1]);
            var trait = TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals(family[0])).findFirst().orElseThrow();
            var effect = trait.getTrait().getEffects().stream().filter(e -> e instanceof WielderEffectTraitEffect).findFirst().orElseThrow();
            var access = (SetAccess) effect;
            boolean ready = !List.of("baseline", "refuse", "refuse-type").contains(System.getProperty("leggings.test.sgSetProofMode"));
            require(access.spike$ready() == ready, "Final LEGS local guard differs from expected candidate/refusal");
            require(SpikePlugin.actualOrder != null, "Actual sorted mixin order was not observed");
            REPORT.append("SET ").append(family[0]).append(" actual order ").append(SpikePlugin.actualOrder).append(" ready=").append(ready).append('\n');
            boolean boots = Boolean.getBoolean("leggings.test.boots");
            if (boots) {
                require((boolean) effect.getClass().getMethod("mismatchedboots$sgSetReady").invoke(effect), "Current Boots final set proof refused compatible LEGS hook");
            }
            var roots = new ArrayList<>(pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)));
            roots.addAll(pair(source, source));
            for (var root : roots) {
                for (int pieces = 1; pieces <= 4; pieces++) {
                    var subject = player(server);
                    var control = player(server);
                    subject.tickCount = 20;
                    control.tickCount = 20;
                    var before = root.copy();
                    var slots = List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
                    var nativePieces = List.of(source, armor("boots", "boot_plates", family[1]),
                            armor("helmet", "helmet_plates", family[1]), armor("chestplate", "chestplate_plates", family[1]));
                    for (int i = 0; i < pieces; i++) {
                        var nativePiece = nativePieces.get(i).copy();
                        subject.setItemSlot(slots.get(i), i == 0 ? root : i == 1 && boots ? mixedBoots(nativePiece) : nativePiece);
                        if (i != 0 || ready) { control.setItemSlot(slots.get(i), nativePiece.copy()); }
                    }
                    var context = new TraitActionContext(subject, trait, SetCandidate.carrier(root));
                    int expectedCount = pieces - (ready ? 0 : 1);
                    require(access.spike$count(context, GearTypes.ARMOR.get(), subject) == expectedCount, "Native count must recognize each mixed slot once");
                    SetCandidate.calls = 0;
                    for (int i = 0; i < pieces; i++) {
                        var stack = subject.getItemBySlot(slots.get(i));
                        if (i == 0) {
                            WearerContracts.observing = true;
                            try { SetCandidate.wearer(root, subject); }
                            finally { WearerContracts.observing = false; }
                        }
                        else if (i == 1 && boots) { stack.inventoryTick(subject.level(), subject, EquipmentSlot.FEET); }
                        else { effect.inventoryTick(new TraitActionContext(subject, trait, stack), subject.level(), subject, true); }
                        var original = control.getItemBySlot(slots.get(i));
                        if (!original.isEmpty()) { effect.inventoryTick(new TraitActionContext(control, trait, original), control.level(), control, true); }
                    }
                    require(SetCandidate.calls == (ready ? 1 : 0), "Duplicate-source Wielder dispatch was not once per trait");
                    require(effects(subject).equals(effects(control)), "Native/mixed Wielder effect parameters differ: " + effects(subject) + " vs " + effects(control));
                    require(ItemStack.matches(root, before) && subject.getItemBySlot(EquipmentSlot.LEGS) == root, "Wielder replaced real equipment or changed root/history");
                    REPORT.append("SET pieces=").append(pieces).append(" count=").append(expectedCount).append(" effects=").append(effects(subject)).append('\n');
                }
                var player = player(server);
                require(!SetCandidate.recognizes(new TraitActionContext(player, trait, source), EquipmentSlot.MAINHAND, root), "Held root counted as LEGS");
                require(!SetCandidate.recognizes(new TraitActionContext(player, trait, source), EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS)), "Foreign root recognized");
            }
        }
    }

    private static List<String> effects(ServerPlayer player) {
        return player.getActiveEffects().stream().map(e -> e.toString()).sorted().toList();
    }

    private static ItemStack mixedBoots(ItemStack source) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    static ItemStack armor(String name, String plates, String material, String... extra) throws Exception {
        var parts = new ArrayList<PartInstance>();
        parts.add(PartInstance.from(part(plates, material)));
        for (int i = 0; i < extra.length; i += 2) { parts.add(PartInstance.from(part(extra[i], extra[i + 1]))); }
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + name))).construct(parts);
    }

    static ItemStack part(String name, String material) throws Exception {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + name));
        return (ItemStack) item.getClass().getMethod("create", List.class).invoke(item, List.of(MaterialInstance.of(DataResource.material("silentgear:" + material))));
    }

    static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "SgLegsSpike");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
        loaded.setAccessible(true);
        loaded.invoke(player.connection);
        player.setGameMode(GameType.SURVIVAL);
        server.overworld().getChunkAt(new BlockPos(0, server.overworld().getMinY() + 3, 0));
        return player;
    }

    public static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
