package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireAbilityInput;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Production input for both actives, shared transport/ticks and normal root synchronization. */
final class NeoForgeJustDireGroundStompTransportTest {
    private static final String PREFIX = "just-dire-ground-stomp-transport";
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static Future<?> job;
    private static boolean initialized, started, finished;
    private static int wait, paused, menuId;
    private static ItemStack serverRoot;
    private static JustDireActiveNetwork.ActivationReply activation;
    private static JustDireActiveNetwork.SettingsReply settings;
    private static JustDireActiveNetwork.Activate consumed;
    private static UUID token;
    private static boolean inputFlight, holdInput;
    private static ItemStack inputRoot;
    private static JustDireActiveNetwork.ActivationReply heldInput;
    private static JustDireActiveNetwork.ActivationReply inputPrepared;
    private static Mob earthquakeTarget;

    private NeoForgeJustDireGroundStompTransportTest() {
    }

    static void tick(Minecraft client) {
        if (finished || client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
            return;
        }
        try {
            if (!initialized) {
                initialize(client);
                initialized = true;
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.get();
                job = null;
            }
            Step step = steps.peek();
            if (step == null) {
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt",
                                             report + "production GS/Earthquake input, shared paid/free snapshots and automatic clocks; native UI belongs to the client gate, no manual acceptance\n");
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
                finished = true;
            } else if (!started) {
                step.start.run();
                started = true;
                wait = 0;
            } else if (step.done.getAsBoolean()) {
                report.append(step.name).append('\n');
                steps.remove();
                started = false;
            } else if (++wait > 600) {
                throw new AssertionError("Timed out: " + step.name + "; charge=" + charge(feet(client))
                                                 + "; Earthquake=" + earthquakeClock(feet(client)) + "; reply=" + activation + "/" + settings);
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(PREFIX, failure);
            finished = true;
        }
        if (finished) {
            JustDireActiveNetwork.clearClientReplies();
            client.stop();
        }
    }

    private static void initialize(Minecraft client) throws Exception {
        require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Client not hidden/muted");
        JustDireGroundStompTestSupport.initializeApi();
        JustDireEarthquakeTestSupport.bind();
        JustDireActiveNetwork.clientReplies(reply -> {
            activation = reply;
            if (inputFlight && reply.prepared() && reply.accepted()) {
                inputPrepared = reply;
            }
            if (inputFlight && holdInput && reply.prepared() && reply.accepted()) {
                heldInput = reply;
            } else if (inputFlight) {
                JustDireAbilityInput.reply(reply);
            }
        }, reply -> settings = reply);
        add("real production prepare/action: one bill and positive timer reach ordinary equipment packets", () -> server(client, player -> {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            // The joint full lockout keeps this client outdoors longer; incidental slime combat is not an input/clock test.
            player.setInvulnerable(true);
            var boot = JustDireGroundStompTestSupport.boots("eclipsealloy", 12_000);
            serverRoot = MixedBootsFactory.createPair(boot, new ItemStack(Items.DIAMOND_BOOTS)).primary();
            configure(serverRoot);
            player.setItemSlot(EquipmentSlot.FEET, serverRoot);
            player.inventoryMenu.broadcastChanges();
        }), () -> client.screen == null && charge(feet(client)) == 6_000);
        add("server-issued nonce travels through bundled CommonNetworking",
            () -> prepare(1),
            () -> activation != null && activation.sequence() == 1 && activation.prepared() && activation.accepted());
        add("prepared permit activates exactly one live root", () -> {
            consumed = new JustDireActiveNetwork.Activate(1, activation.nonce(), 71, false, java.util.List.of(36));
            activation = null;
            send(consumed);
        }, () -> activation != null && !activation.prepared() && activation.accepted() && charge(feet(client)) == 1_000 && timer(feet(client)) > 0);
        add("consumed activation replay rejected through real transport while normal timer progresses", () -> {
                activation = null;
                send(consumed);
            },
            () -> activation != null && !activation.accepted() && charge(feet(client)) == 1_000);
        add("unequipped root pauses through natural server ticks", () -> server(client, player -> {
            paused = timer(serverRoot);
            require(paused > 0, "No pending timer to pause");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.getInventory().setItem(2, serverRoot);
            player.inventoryMenu.broadcastChanges();
        }), () -> feet(client).isEmpty() && timer(client.player.getInventory().getItem(2)) == paused && wait > 30);
        add("paused root was not advanced; disabled/depleted re-equipped root resumes", () -> server(client, player -> {
            require(timer(serverRoot) == paused, "Unequipped timer advanced");
            set(serverRoot, "tool_enabled", false);
            set(serverRoot, "groundstomp_upgrade_installed", false);
            set(serverRoot, "forge_energy", 0);
            player.getInventory().setItem(2, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET, serverRoot);
            player.inventoryMenu.broadcastChanges();
        }), () -> charge(feet(client)) == 0 && timer(feet(client)) > 0 && timer(feet(client)) < paused);
        add("production worn timer expires naturally even while tool/installation disabled and FE exhausted",
            () -> {},
            () -> !feet(client).isEmpty() && timer(feet(client)) == -1);
        add("consumed replay still rejects after actual natural cooldown expiry", () -> {
                activation = null;
                send(consumed);
            },
            () -> activation != null && !activation.accepted() && charge(feet(client)) == 0 && timer(feet(client)) == -1);
        add("native settings menu opens normally, with no mixed UI hook",
            () -> {
                server(client, player -> {
                    set(serverRoot, "groundstomp_upgrade_installed", true);
                    player.inventoryMenu.broadcastChanges();
                });
                nativeMenu();
            },
            () -> client.screen instanceof AbstractContainerScreen<?> screen && screen.getClass()
                                                                                      .getName()
                                                                                      .equals("com.direwolf20.justdirethings.client.screens.ToolSettingScreen"));
        add("production selected-root settings token through bundled transport", () -> {
            menuId = ((AbstractContainerScreen<?>) client.screen).getMenu().containerId;
            settings = null;
            send(request(1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        }, () -> settings != null && settings.accepted() && settings.request() == 1);
        add("zero FE retains full tier settings range; normal inventory packets carry edit", () -> {
            token = settings.token();
            settings = null;
            send(request(2, token, 2, "groundstomp", 5, 71, false, true));
        }, () -> settings != null && settings.request() == 2 && settings.accepted() && value(feet(client)) == 5);
        add("forged mouse setting rejected without resetting accepted root value/resources", () -> {
            settings = null;
            send(request(3, token, 4, "groundstomp", 2, 71, true, true));
        }, () -> settings != null && settings.request() == 3 && !settings.accepted() && value(feet(client)) == 5 && charge(feet(client)) == 0);
        add("selection token cannot authorize action", () -> {
                activation = null;
                send(new JustDireActiveNetwork.Activate(1, token, 71, false, java.util.List.of(36)));
            },
            () -> activation != null && !activation.accepted());
        add("ordinary close packet invalidates edit session", () -> client.screen.onClose(), () -> client.screen == null);
        add("stale edit after actual menu close rejects through transport", () -> {
                settings = null;
                send(request(4, token, 1, "groundstomp", 0, 71, false, true));
            },
            () -> settings != null && settings.request() == 4 && !settings.accepted() && value(feet(client)) == 5);
        add("server/client provenance and live resource state agree after action/edits/ticking", () -> server(client, player -> {
            require(player.getItemBySlot(EquipmentSlot.FEET) == serverRoot && charge(serverRoot) == 0 && timer(serverRoot) == -1
                            && value(serverRoot) == 5 && ModContent.getMixedBootsData(serverRoot).equals(ModContent.getMixedBootsData(feet(client))),
                    "Live root/state/provenance diverged");
        }), () -> true);
        earthquake(client);
    }

    private static void earthquake(Minecraft client) {
        add("seed a real equipped root with an independently ticking Earthquake clock and paid-only GS binding", () -> server(client, player -> {
            try {
                serverRoot = JustDireEarthquakeTestSupport.mixed(150_000, false, false);
                set(serverRoot, "groundstomp_upgrade_installed", true);
                set(serverRoot, "groundstomp_bindingmode", 2);
                set(serverRoot, "stepheight_upgrade_installed", true);
                set(serverRoot, "stepheight_bindingmode", 2);
                set(serverRoot, "jumpboost_upgrade_installed", true);
                set(serverRoot, "jumpboost_bindingmode", 2);
                set(serverRoot, "ability_bindings", List.of(JustDireEarthquakeTestSupport.binding("groundstomp", 71),
                                                            JustDireEarthquakeTestSupport.binding("earthquake", 72),
                                                            JustDireEarthquakeTestSupport.binding("stepheight", 73),
                                                            JustDireEarthquakeTestSupport.binding("jumpboost", 73)));
                serverRoot.set(JustDireEarthquakeTestSupport.timers(), List.of(JustDireEarthquakeTestSupport.timer("earthquake", 77, true)));
                player.setItemSlot(EquipmentSlot.FEET, serverRoot);
                player.inventoryMenu.broadcastChanges();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }), () -> charge(feet(client)) == 150_000 && earthquakeClock(feet(client)) != null && earthquakeClock(feet(client)).ticks() < 70);
        add("fixture direct-driver sequence cannot be reused by the production input listener", () -> {
            inputFlight = true;
            activation = null;
            press(71);
        }, () -> activation != null && activation.prepared() && !activation.accepted());
        add("hold a real free passive reply while Ground Stomp is ready on a different key", () -> {
            holdInput = true;
            heldInput = null;
            activation = null;
            press(73);
        }, () -> heldInput != null);
        add("ordinary charging/peer-clock packets cannot cancel that unrelated free switch", () -> server(client, player -> {
            try (var transaction = Transaction.openRoot()) {
                require(JustDireResourceTestSupport.handler(serverRoot).insert(1_000, transaction) == 1_000, "Fixture recharge failed");
                transaction.commit();
            }
            player.inventoryMenu.broadcastChanges();
        }), () -> charge(feet(client)) == 151_000 && wait > 5);
        add("production client forwards the free reply; Step/powered Jump toggle once despite charging and other ready active bindings", () -> {
            holdInput = false;
            activation = null;
            JustDireAbilityInput.reply(heldInput);
        }, () -> activation != null && !activation.prepared() && activation.accepted() && charge(feet(client)) == 151_000
                && !feet(client).getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true)
                && !feet(client).getOrDefault(JustDireAttributeTestSupport.<Boolean>component("jumpboost_toggle"),
                                              true) && timer(feet(client)) == -1);
        add("restore funded settings and let ordinary derived-attribute packets settle before the paid flight",
            () -> server(client, player -> {
                set(serverRoot, "forge_energy", 150_000);
                set(serverRoot, "stepheight_toggle", true);
                player.inventoryMenu.broadcastChanges();
            }),
            () -> charge(feet(client)) == 150_000 && feet(client).getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"),
                                                                               false) && wait > 5);
        add("hold a genuine paid-only GS reply while normal Earthquake slot packets recreate the client root", () -> {
            holdInput = true;
            heldInput = null;
            activation = null;
            inputRoot = feet(client);
            press(71);
        }, () -> heldInput != null && feet(client) != inputRoot && wait > 15);
        add("release the real reply into production client validation; server pays once despite unrelated clock progression", () -> {
            holdInput = false;
            activation = null;
            JustDireAbilityInput.reply(heldInput);
        }, () -> activation != null && !activation.prepared() && activation.accepted() && charge(feet(client)) == 145_000 && timer(feet(client)) > 0);
        add("replay that consumed delayed permit still rejects through bundled transport", () -> {
            activation = null;
            send(new JustDireActiveNetwork.Activate(heldInput.sequence(), heldInput.nonce(), 71, false, java.util.List.of(36)));
        }, () -> activation != null && !activation.accepted() && charge(feet(client)) == 145_000);
        add("seed a funded Earthquake root and one actual grounded world target for production input", () -> {
            inputFlight = false;
            server(client, player -> {
                try {
                    player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8)).forEach(Mob::discard);
                    serverRoot = JustDireEarthquakeTestSupport.mixed(150_000, true, false);
                    player.setItemSlot(EquipmentSlot.FEET, serverRoot);
                    earthquakeTarget = EntityType.COW.create(player.level(), EntitySpawnReason.COMMAND);
                    require(earthquakeTarget != null, "No real target");
                    // Fix target geometry/grounded state, as in the server comparisons; this gate tests transport, not mob physics.
                    earthquakeTarget.setNoAi(true);
                    earthquakeTarget.setNoGravity(true);
                    earthquakeTarget.setInvulnerable(true);
                    earthquakeTarget.setPos(player.getX() + 1, player.getY(), player.getZ());
                    require(player.level().addFreshEntity(earthquakeTarget), "Target not inserted");
                    earthquakeTarget.setOnGround(true);
                    player.inventoryMenu.broadcastChanges();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }, () -> charge(feet(client)) == 150_000 && client.level.getEntity(earthquakeTarget.getId()) instanceof Mob);
        add("production Earthquake input prepares/activates through actual CommonNetworking", () -> {
            server(client, player -> require(earthquakeTarget.onGround() && earthquakeTarget.distanceToSqr(player) < 25,
                                             "Transport target not ready: ground=" + earthquakeTarget.onGround() + ", target=" + earthquakeTarget.position()
                                                     + ", player=" + player.position() + ", loaded=" + player.level()
                                                                                                             .areEntitiesActuallyLoadedAndTicking(
                                                                                                                     earthquakeTarget.chunkPosition())));
            inputFlight = true;
            inputPrepared = null;
            activation = null;
            press(72);
        }, () -> activation != null && !activation.prepared() && activation.accepted() && charge(feet(client)) == 100_000
                && earthquakeClock(feet(client)) != null);
        add("real world target received Slowness IV on the authoritative server", () -> server(client, player -> {
            consumed = new JustDireActiveNetwork.Activate(inputPrepared.sequence(), inputPrepared.nonce(), 72, false, java.util.List.of(36));
            // Vanilla does not synchronize a normal mob's effect map to non-passenger clients.
            var effect = earthquakeTarget.getEffect(MobEffects.SLOWNESS);
            require(effect != null && effect.getAmplifier() == 3 && charge(serverRoot) == 100_000, "Missing authoritative effect/debit");
        }), () -> true);
        add("real automatic worn ticker transitions active Earthquake to native 800-tick cooldown", () -> {},
            () -> earthquakeClock(feet(client)) != null && !earthquakeClock(feet(client)).active() && charge(feet(client)) == 100_000);
        add("automatic inactive clock advances through ordinary slot packets without another debit", () -> {},
            () -> earthquakeClock(feet(client)) != null && earthquakeClock(feet(client)).ticks() < 400 && charge(feet(client)) == 100_000);
        add("full automatic Earthquake lockout expires exactly once on the real server player", () -> {},
            () -> earthquakeClock(feet(client)) == null && charge(feet(client)) == 100_000);
        add("consumed Earthquake nonce cannot reactivate after natural expiry", () -> {
                activation = null;
                send(consumed);
            },
            () -> activation != null && !activation.accepted() && earthquakeClock(feet(client)) == null && charge(feet(client)) == 100_000);
    }

    private static void press(int key) {
        JustDireAbilityInput.key(new InputEvent.Key(new KeyEvent(key, 0, 0), GLFW.GLFW_PRESS));
    }

    private static JustDireEarthquakeTestSupport.Clock earthquakeClock(ItemStack root) {
        try {
            return JustDireEarthquakeTestSupport.clock(root, "earthquake");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void configure(ItemStack root) {
        try {
            set(root, "groundstomp_upgrade_installed", true);
            set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                                                       .getConstructor(String.class, int.class, boolean.class, boolean.class)
                                                       .newInstance("groundstomp", 71, false, true)));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void nativeMenu() {
        try {
            ClientPacketDistributor.sendToServer((CustomPacketPayload) Class.forName(
                    "com.direwolf20.justdirethings.common.network.data.ToolSettingsGUIPayload").getConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static JustDireActiveNetwork.SettingsRequest request(int number,
                                                                 UUID token,
                                                                 int operation,
                                                                 String ability,
                                                                 int value,
                                                                 int key,
                                                                 boolean mouse,
                                                                 boolean equipped) {
        return new JustDireActiveNetwork.SettingsRequest(menuId, 36, 1, number, token, operation, ability, value, key, mouse, equipped);
    }

    private static void prepare(int sequence) {
        activation = null;
        send(new JustDireActiveNetwork.Prepare(sequence, 71, false));
    }

    private static void send(Object packet) {
        Network.getNetworkHandler().sendToServer(packet);
    }

    private static void server(Minecraft client, Consumer<ServerPlayer> task) {
        var id = client.player.getUUID();
        var server = client.getSingleplayerServer();
        require(server != null, "No integrated server");
        job = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            require(player != null, "No actual server player");
            task.accept(player);
        });
    }

    private static ItemStack feet(Minecraft client) {
        return client.player.getItemBySlot(EquipmentSlot.FEET);
    }

    private static int charge(ItemStack root) {
        return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
    }

    private static int value(ItemStack root) {
        return root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_value"), -1);
    }

    private static int timer(ItemStack root) {
        if (root.isEmpty()) {
            return -1;
        }
        try {
            return JustDireGroundStompTestSupport.timer(root);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static <T> void set(ItemStack root, String path, T value) {
        JustDireAttributeTestSupport.set(root, path, value);
    }

    private static void add(String name, Runnable start, BooleanSupplier done) {
        steps.add(new Step(name, start, done));
    }

    private static void require(boolean condition, String text) {
        if (!condition) {
            throw new AssertionError(text);
        }
    }

    private record Step(String name, Runnable start, BooleanSupplier done) {
    }
}
