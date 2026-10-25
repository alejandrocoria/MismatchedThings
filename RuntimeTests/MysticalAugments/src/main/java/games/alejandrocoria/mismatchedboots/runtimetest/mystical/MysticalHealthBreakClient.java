package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.require;

/** Mixed cleanup regression plus an unchanged native-only control of the upstream ordering defect. */
public final class MysticalHealthBreakClient {
    private static final String OUTPUT = "mystical-health-break";
    private static final boolean CLIENT_CACHE = Boolean.getBoolean("mismatchedboots.test.mysticalClientCache");
    private static final StringBuilder REPORT = new StringBuilder();
    private static Future<?> job;
    private static ItemStack boots;
    private static int phase, next, age, ticks, scene;
    private static float minimumHealth;
    private static boolean deathScreen;
    private static boolean finished;

    private MysticalHealthBreakClient() {
    }

    private static int kind() {
        return scene >= 6 ? (scene + 1) % 3 : scene % 3;
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 2000, "Health break diagnostic timeout");
            if (client.player == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            client.options.pauseOnLostFocus = false;
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.get();
                job = null;
                phase = next;
                age = 0;
            }
            age++;
            if (phase == 3) {
                minimumHealth = Math.min(minimumHealth, client.player.getHealth());
                deathScreen |= client.screen instanceof net.minecraft.client.gui.screens.DeathScreen;
            }
            if (phase == 0) {
                queue(client, () -> {
                    var owner = owner(client);
                    owner.setGameMode(GameType.SURVIVAL);
                    owner.setNoGravity(true);
                    owner.setInvulnerable(false);
                    owner.getFoodData().setFoodLevel(10);
                    owner.getInventory().clearContent();
                    owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    owner.removeAllEffects();
                }, 1);
            } else if (phase == 1 && age >= 30) {
                // Diagnostic recovery only: the server must still be alive before repairing a client-only death.
                require(owner(client).isAlive(), "Server unexpectedly dead before next control");
                client.player.setHealth(20);
                client.setScreen(null);
                queue(client, () -> {
                    var nativeBoots = MysticalAugmentsProbe.item("awakened_supremium_boots", "HEALTH_BOOST_V", "STEP_ASSIST");
                    if (kind() == 0) {
                        boots = nativeBoots;
                    } else {
                        var pair = new MixedBootsWorkbenchRecipe().assemblePair(new MismatchedWorkbenchInput(nativeBoots,
                                                                                                             new ItemStack(Items.DIAMOND_BOOTS)));
                        require(pair.isComplete(), "Recipe rejected health break control");
                        boots = kind() == 1 ? pair.primary() : pair.inverse();
                    }
                    boots.remove(DataComponents.UNBREAKABLE);
                    boots.setDamageValue(boots.getMaxDamage() - 1);
                    owner(client).setItemSlot(EquipmentSlot.FEET, scene == 4 || scene == 5 ? nativeBoots : boots);
                    owner(client).setHealth(20);
                }, 2);
            } else if (phase == 2 && age >= 30) {
                require(client.player.getMaxHealth() == 40 && owner(client).getMaxHealth() == 40, "Health V control not synchronized");
                queue(client, () -> {
                    if (scene == 4 || scene == 5) {
                        owner(client).setItemSlot(EquipmentSlot.FEET, boots);
                        REPORT.append("native -> mixed with same cached Health V, no unequip gap\n");
                    }
                    owner(client).setHealth(scene >= 6 ? 20 : 40);
                }, 5);
            } else if (phase == 5 && age >= 25) {
                require(client.player.getHealth() == (scene >= 6 ? 20 : 40), "Initial boosted health did not synchronize before hit");
                if (scene >= 3) {
                    // Controlled ordering probe, NOT an assertion that normal packets arrived in this order:
                    // emulate an attribute snapshot already lacking the bonus before equipment/cache cleanup.
                    client.player.getAttribute(Attributes.MAX_HEALTH).removeModifier(Identifier.parse("mysticalagriculture:health_boost_augment"));
                    require(client.player.getMaxHealth() == 20, "Controlled synchronized snapshot still has bonus");
                }
                minimumHealth = client.player.getHealth();
                deathScreen = false;
                queue(client, () -> {
                    var owner = owner(client);
                    owner.invulnerableTime = 0;
                    require(owner.hurtServer(owner.level(), owner.damageSources().playerAttack(owner), 4), "Actual armor-damaging hit refused");
                    require(boots.isEmpty(), "Incoming hit did not break near-broken armor");
                    REPORT.append("scene=").append(scene).append(" immediate serverHealth=").append(owner.getHealth()).append('\n');
                }, 3);
            } else if (phase == 3 && age >= 40) {
                queue(client, () -> {
                    var owner = owner(client);
                    REPORT.append("scene=").append(scene).append(" kind=").append(kind())
                          .append(" controlledAttributeOrder=").append(scene >= 3).append(" serverHealth=")
                          .append(owner.getHealth()).append(" serverMax=").append(owner.getMaxHealth())
                          .append(" serverDead=").append(owner.isDeadOrDying())
                          .append(" clientHealth=").append(client.player.getHealth()).append(" clientMax=").append(client.player.getMaxHealth())
                          .append(" clientDead=").append(client.player.isDeadOrDying()).append(" minimumClientHealth=").append(minimumHealth)
                          .append(" deathScreen=").append(deathScreen).append('\n');
                    require(owner.isAlive(), "Incoming nonlethal hit killed server player");
                    if (kind() != 0 || scene < 3) {
                        require(minimumHealth > 0 && !deathScreen && !client.player.isDeadOrDying(), "Mixed cleanup caused client-only death");
                        require(Math.abs(owner.getHealth() - client.player.getHealth()) < .001f,
                                "Mixed cleanup left client/server health desynchronized");
                    }
                    scene++;
                }, 4);
            } else if (phase == 4) {
                if (scene < 9) {
                    phase = 0;
                    age = 0;
                } else if (CLIENT_CACHE) {
                    require(client.player.isDeadOrDying() && client.screen instanceof net.minecraft.client.gui.screens.DeathScreen,
                            "Controlled native ordering did not reproduce persistent client-only death");
                    client.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    phase = 6;
                    age = 0;
                } else {
                    require(!client.player.isDeadOrDying() && !deathScreen, "Server-only native handler caused client-only death");
                    REPORT.append("Fabric native handler has no client cache; no local removal/death is expected\n");
                    phase = 7;
                }
            } else if (phase == 6 && age >= 25) {
                queue(client, () -> {
                    require(owner(client).isAlive() && client.player.isDeadOrDying(), "Client-only death did not survive respawn request");
                    REPORT.append("Controlled native Health V: server remains alive; real client respawn request does not resolve client-only death\n");
                }, 7);
            } else if (phase == 7) {
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "diagnostic completed; controlled ordering is not ordinary acceptance\n");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.stop();
        }
    }

    private static ServerPlayer owner(Minecraft client) {
        return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
    }

    private static void queue(Minecraft client, Action action, int target) {
        job = client.getSingleplayerServer().submit(() -> {
            try {
                action.run();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        next = target;
    }

    private interface Action {
        void run() throws Exception;
    }
}
