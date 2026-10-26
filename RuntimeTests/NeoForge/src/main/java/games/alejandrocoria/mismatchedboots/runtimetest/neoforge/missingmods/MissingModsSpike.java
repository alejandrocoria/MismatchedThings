package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsInteractionChecks;
import games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsProof;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.UUID;

/** Loader registration only; all recovery behavior lives in the shared proof/model. */
public final class MissingModsSpike {
    private MissingModsSpike() {
    }

    public static boolean enabled() {
        return MissingModsProof.enabled();
    }

    public static void tick(MinecraftServer server) {
        MissingModsProof.tick(server);
    }

    public static void checkResources(MinecraftServer server, ItemStack active) {
        var player = new ServerPlayer(server,
                                      server.overworld(),
                                      new GameProfile(UUID.randomUUID(), "recovery-energy"),
                                      ClientInformation.createDefault());
        player.getInventory().setItem(0, active.copy());
        var access = ItemAccess.forPlayerSlot(player, 0);
        var retained = access.getCapability(Capabilities.Energy.ITEM);
        if (retained == null || retained.getAmountAsLong() != 12_347) {
            throw new AssertionError("Active production FE capability unavailable");
        }

        var suspended = MissingModsInteractionChecks.suspend(server, active);
        player.getInventory().setItem(0, suspended);
        if (access.getCapability(Capabilities.Energy.ITEM) != null || retained.getCapacityAsLong() != 0 || retained.getAmountAsLong() != 0) {
            throw new AssertionError("Suspended production root exposes FE capability/wallet");
        }

        var before = suspended.copy();
        try (var transaction = Transaction.openRoot()) {
            if (retained.insert(5_000, transaction) != 0 || retained.extract(5_000, transaction) != 0) {
                throw new AssertionError("Retained charger handler paid a suspended root");
            }
            transaction.commit();
        }
        if (!ItemStack.matches(before, player.getInventory().getItem(0))) {
            throw new AssertionError("Refused FE transfer mutated archived state");
        }
    }

    public static void initialize(IEventBus bus) {
        if (!MissingModsProof.fixturesPresent()) {
            return;
        }

        var attributes = DeferredRegister.create(Registries.ATTRIBUTE, MissingModsProof.MOD);
        attributes.register("temporary_attribute", () -> new RangedAttribute("missing-mod-proof", 0, -1024, 1024));
        attributes.register(bus);
        var sounds = DeferredRegister.create(Registries.SOUND_EVENT, MissingModsProof.MOD);
        sounds.register("temporary_equip", () -> SoundEvent.createVariableRangeEvent(Identifier.parse(MissingModsProof.MOD + ":temporary_equip")));
        sounds.register(bus);
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MissingModsProof.MOD);
        components.register("temporary_energy", () -> DataComponentType.<Integer>builder()
                                                                       .persistent(com.mojang.serialization.Codec.INT)
                                                                       .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                                                                       .build());
        components.register("throwing_property", MissingModsProof::throwingProperty);
        components.register("converting_property", MissingModsProof::convertingProperty);
        components.register(bus);
    }
}
