package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/** Real native damage dispatch and sound-event observation, without replacing Oritech's handler. */
public final class NeoForgeOritechFallTest {
    private static boolean finished;
    private static boolean capture;
    private static int sounds;
    private static int cases;

    private NeoForgeOritechFallTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeOritechFallTest::sound);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void sound(PlayLevelSoundEvent.AtPosition event) {
        if (capture && event.getSound() != null && Identifier.fromNamespaceAndPath("oritech", "short_servo")
                                                             .equals(BuiltInRegistries.SOUND_EVENT.getKey(event.getSound().value()))) {
            require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == .2F
                            && event.getOriginalPitch() == 1F, "Native landing sound parameters changed");
            sounds++;
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }

        finished = true;
        try {
            boolean absent = Boolean.getBoolean("mismatchedboots.runtimetest.oritechAbsent");
            require(ModList.get().isLoaded("oritech") != absent, "Wrong Oritech environment");

            ServerLevel level = server.overworld();
            var vanillaPair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            check(level, vanillaPair.primary(), EquipmentSlot.FEET, false, false);
            check(level, vanillaPair.inverse(), EquipmentSlot.FEET, false, false);

            if (!absent) {
                var exoItem = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("oritech", "exo_boots"))
                                                    .orElseThrow().value();
                var exo = new ItemStack(exoItem);
                var pair = MixedBootsFactory.createPair(exo, new ItemStack(Items.DIAMOND_BOOTS));

                require(ItemStack.matches(exo, new ItemStack(exoItem)), "Creation mutated the native input");
                require(pair.primary().get(DataComponents.EQUIPPABLE).equipSound()
                            .equals(exo.get(DataComponents.EQUIPPABLE).equipSound()), "Left equip sound changed");
                require(pair.inverse().get(DataComponents.EQUIPPABLE).equipSound()
                            .equals(exo.get(DataComponents.EQUIPPABLE).equipSound()),
                        "Right Exo did not supply the only custom equip sound");

                for (ItemStack boots : List.of(exo, pair.primary(), pair.inverse(),
                                               MixedBootsFactory.createPair(exo, exo).primary())) {
                    check(level, boots, EquipmentSlot.FEET, false, true);
                    check(level, boots, EquipmentSlot.FEET, true, false);
                }

                check(level, pair.primary(), EquipmentSlot.OFFHAND, false, false);
                check(level, pair.inverse(), EquipmentSlot.MAINHAND, false, false);

                ItemStack badCount = pair.primary().copy();
                badCount.setCount(2);
                check(level, badCount, EquipmentSlot.FEET, false, false);

                ItemStack missingData = pair.primary().copy();
                missingData.remove(ModContent.mixedBootsDataType().orElseThrow());
                check(level, missingData, EquipmentSlot.FEET, false, false);

                ItemStack spoof = new ItemStack(Items.STICK);
                spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(pair.primary()).orElseThrow());
                check(level, spoof, EquipmentSlot.FEET, false, false);
            }

            NetworkAcceptanceFiles.write("oritech-fall-report.txt", cases + " real damage cases; "
                    + (absent ? "optional absence, ordinary inverse pair\n"
                              : "native Exo + both mixed orientations + two Exo; repeated falls; non-fall damage; "
                                + "wrong slots, missing data, bad count and spoof rejection; single native servo emission; "
                                + "shared custom equip sound and immutable provenance\n"));
            NetworkAcceptanceFiles.write("oritech-fall-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("oritech-fall", failure);
        } finally {
            capture = false;
            server.halt(false);
        }
    }

    private static void check(ServerLevel level, ItemStack input, EquipmentSlot slot, boolean genericDamage, boolean expectFallProtection) {
        var subject = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        require(subject != null, "Could not create damage subject");
        subject.setNoAi(true);

        ItemStack boots = input.copy();
        var sourceHistory = ModContent.getMixedBootsData(boots).orElse(null);
        subject.setItemSlot(slot, boots);

        sounds = 0;
        capture = true;
        try {
            float healthBefore = subject.getHealth();
            if (genericDamage) {
                subject.hurtServer(level, level.damageSources().generic(), 6);
            } else {
                subject.causeFallDamage(9, 1, level.damageSources().fall());
            }

            require(expectFallProtection ? subject.getHealth() == healthBefore : subject.getHealth() < healthBefore,
                    "Wrong damage result: slot=" + slot + ", generic=" + genericDamage + ", stack=" + input);
            require(sounds == (expectFallProtection ? 1 : 0), "Wrong native servo emission count: " + sounds);

            if (expectFallProtection) {
                subject.causeFallDamage(9, 1, level.damageSources().fall());
                require(subject.getHealth() == healthBefore && sounds == 2, "Fall protection acquired a cooldown/payment");
                require(ItemStack.matches(input, boots), "Protected fall consumed or changed the root");
            }

            require(subject.getItemBySlot(slot) == boots, "Native equipment was substituted");
            require(sourceHistory == null || sourceHistory.equals(ModContent.getMixedBootsData(boots).orElseThrow()),
                    "Damage mutated source provenance");
            cases++;
        } finally {
            capture = false;
            subject.discard();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
