package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;

/** Disposable registrations, omitted between real JVMs; no fixture supplies a recovery decoder. */
public final class RecoveryFixture {
    public static final Identifier SOURCE = Identifier.parse("leggings_skeleton_test:temporary_leggings");
    public static final Identifier WALLET_ID = Identifier.parse("leggings_skeleton_test:wallet");
    public static int walletReads;
    public static final DataComponentType<Long> WALLET = DataComponentType.<Long>builder()
            .persistent(Codec.LONG.flatXmap(value -> {
                walletReads++;
                return stage().equals("partial") ? DataResult.error(() -> "Controlled incomplete foreign read")
                        : DataResult.success(value);
            }, DataResult::success)).networkSynchronized(ByteBufCodecs.VAR_LONG).build();

    private RecoveryFixture() {
    }

    public static String stage() {
        return System.getProperty("leggings.test.recoveryStage", "");
    }

    public static boolean enabled() {
        return !stage().isEmpty();
    }

    public static boolean sourcesPresent() {
        return enabled() && !stage().startsWith("absent");
    }

    public static boolean walletPresent() {
        return sourcesPresent() && !stage().equals("sources");
    }

    public static boolean connected() {
        return stage().equals("write") || stage().equals("absent") || stage().equals("restore");
    }

    public static Item source() {
        return new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SOURCE))
                .durability(500).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build()));
    }
}
