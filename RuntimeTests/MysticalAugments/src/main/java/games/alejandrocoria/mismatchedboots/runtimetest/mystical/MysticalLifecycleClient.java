package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.require;

/** Real integrated connection in two JVMs; native ticks, packets and keyboard movement own behavior. */
public final class MysticalLifecycleClient {
    private static final boolean REJOIN = MysticalLifecycle.phase().equals("rejoin");
    private static final String OUTPUT = "mystical-" + MysticalLifecycle.phase();
    private static final StringBuilder REPORT = new StringBuilder();
    private static List<ItemStack> roots, visuals;
    private static ServerPlayer beforeDeath;
    private static JsonArray beforeDeathRoots;
    private static Object clientBeforeDeath;
    private static Future<?> job;
    private static int phase, nextPhase, ticks, age, healthScene, jumpScene, settled, picture;
    private static double expectedHealth, expectedStep, nativeHealth, nativeStep, baseStep, peak, nativePeak;
    private static boolean finished, capturePending;
    private static volatile boolean captureDone;
    private static volatile Throwable captureFailure;
    private static Scene scene;

    private MysticalLifecycleClient() {
    }

    public static void tick(Minecraft client) {
        if (MysticalLifecycle.phase().equals("healthbreak")) {
            MysticalHealthBreakClient.tick(client);
            return;
        }
        if (finished) {
            return;
        }
        try {
            require(++ticks < 4000, "MA connected timeout phase=" + phase + " age=" + age);
            if (client.screen instanceof BackupConfirmScreen backup) {
                backup.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(
                              Component.translatable("selectWorld.backupJoinSkipButton"))).map(Button.class::cast)
                      .findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                client.setScreen(null);
            }
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.get();
                job = null;
                phase = nextPhase;
                age = 0;
            }
            age++;
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Client not hidden/muted");
                client.options.guiScale().set(2);
                client.resizeGui();
                client.player.input = new KeyboardInput(client.options);
                MysticalLifecycle.playerId = client.player.getUUID();
                queue(client, () -> initialize(client), 1);
            } else if (phase == 1 && age >= 25) {
                queue(client, () -> {
                    var owner = owner(client);
                    expectedHealth = owner.getMaxHealth();
                    expectedStep = owner.getAttributeValue(Attributes.STEP_HEIGHT);
                    if (healthScene == 0) {
                        nativeHealth = expectedHealth;
                        nativeStep = expectedStep;
                        require(nativeHealth > 20 && nativeStep > .6, "Native Health/Step control inactive");
                    } else if (healthScene < 3) {
                        require(close(expectedHealth, nativeHealth) && close(expectedStep, nativeStep), "Mixed Health/Step differs from native");
                    } else {
                        require(close(expectedHealth, 20), "Native cache failed unequip cleanup");
                        baseStep = expectedStep;
                    }
                }, 2);
            } else if (phase == 2) {
                if (!close(client.player.getMaxHealth(), expectedHealth) || !close(client.player.getAttributeValue(Attributes.STEP_HEIGHT),
                                                                                   expectedStep)) {
                    require(age < 120, "Connected Health/Step did not synchronize");
                    return;
                }
                REPORT.append("ATTR scene=")
                      .append(healthScene)
                      .append(" maxHealth=")
                      .append(expectedHealth)
                      .append(" step=")
                      .append(expectedStep)
                      .append(" client/server agree\n");
                healthScene++;
                if (healthScene < 4) {
                    queue(client, () -> prepareHealth(client), 1);
                } else {
                    queue(client, () -> prepareJump(client), 3);
                }
            } else if (phase == 3) {
                var effect = client.player.getEffect(MobEffects.JUMP_BOOST);
                if (effect == null || effect.getAmplifier() != 2 || !client.player.onGround() || Math.abs(client.player.getY() - 81) > .02) {
                    require(age < 150, "Jump/equipment/position did not synchronize");
                    return;
                }
                if (++settled < 6) {
                    return;
                }
                peak = 0;
                MysticalLifecycle.serverPeak = 0;
                MysticalLifecycle.recordJump = true;
                client.options.keyJump.setDown(true);
                phase = 4;
                age = settled = 0;
            } else if (phase == 4) {
                client.options.keyJump.setDown(false);
                peak = Math.max(peak, client.player.getY() - 81);
                require(age < 120, "Connected keyboard jump never landed");
                if (age < 15 || !client.player.onGround()) {
                    return;
                }
                if (++settled < 5) {
                    return;
                }
                MysticalLifecycle.recordJump = false;
                require(peak > 2 && Math.abs(peak - MysticalLifecycle.serverPeak) < .3, "Actual keyboard ascent did not reach server");
                require(close(client.player.getHealth(), 20), "Native/mixed No Fall Damage did not preserve connected health");
                if (jumpScene == 0) {
                    nativePeak = peak;
                } else {
                    require(Math.abs(peak - nativePeak) < .15, "Mixed jump differs from native");
                }
                REPORT.append("JUMP scene=")
                      .append(jumpScene)
                      .append(" apex=")
                      .append(peak)
                      .append(" server=")
                      .append(MysticalLifecycle.serverPeak)
                      .append(" native input/packets\n");
                jumpScene++;
                settled = 0;
                if (jumpScene < 3) {
                    queue(client, () -> prepareJump(client), 3);
                } else if (REJOIN) {
                    queue(client, () -> refundsAfterRestart(client), 99);
                } else {
                    clientBeforeDeath = client.player;
                    queue(client, () -> {
                        stash(owner(client));
                        beforeDeath = owner(client);
                        beforeDeathRoots = MysticalLifecycle.encode(client.getSingleplayerServer(), roots);
                        equip(beforeDeath, roots.getFirst(), 0);
                        beforeDeath.setInvulnerable(false);
                    }, 9);
                }
            } else if (phase == 9 && age >= 25) {
                require(close(client.player.getMaxHealth(), nativeHealth) && close(client.player.getAttributeValue(Attributes.STEP_HEIGHT),
                                                                                   nativeStep),
                        "Death control lacks active synchronized Health/Step");
                queue(client, () -> {
                    require(close(beforeDeath.getMaxHealth(), nativeHealth) && close(beforeDeath.getAttributeValue(Attributes.STEP_HEIGHT),
                                                                                     nativeStep),
                            "Death control lacks active native server modifiers");
                    beforeDeath.hurtServer(beforeDeath.level(), beforeDeath.damageSources().genericKill(), 1000);
                    require(beforeDeath.isDeadOrDying(), "Actual death control did not die");
                }, 5);
            } else if (phase == 5) {
                if (!client.player.isDeadOrDying()) {
                    require(age < 100, "Death did not reach client");
                    return;
                }
                client.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                phase = 6;
                age = 0;
            } else if (phase == 6) {
                if (client.player == clientBeforeDeath || !client.player.isAlive()) {
                    require(age < 150, "Real respawn packet did not replace client player");
                    return;
                }
                queue(client, () -> {
                    var owner = owner(client);
                    require(owner != beforeDeath && owner.getUUID().equals(beforeDeath.getUUID()), "Respawn did not replace real server player");
                    roots.set(0, owner.getItemBySlot(EquipmentSlot.FEET));
                    for (int i = 1; i < 4; i++) {
                        roots.set(i, owner.getInventory().getItem(9 + i));
                    }
                    require(roots.stream().allMatch(ModContent::isMixedBoots), "Death lost kept mixed roots");
                    require(MysticalLifecycle.encode(client.getSingleplayerServer(), roots).equals(beforeDeathRoots),
                            "Real respawn changed complete root/purchase history");
                    stash(owner);
                    owner.setNoGravity(true);
                    owner.connection.teleport(.5, 81, .5, 0, 0);
                    REPORT.append(
                            "RESPAWN actual lethal damage and client request replace both player objects; complete kept root/purchase data unchanged\n");
                }, 7);
            } else if (phase == 7 && age >= 25) {
                require(close(client.player.getMaxHealth(), 20) && close(client.player.getAttributeValue(Attributes.STEP_HEIGHT), baseStep),
                        "Respawn/unequip left stale client modifiers");
                visuals = MysticalTooltipChecks.run(client);
                REPORT.append(
                        "TOOLTIP normal/advanced, both inverses, native source lists/styles, no root duplication, hidden/empty/read-only checks pass\n");
                phase = 8;
                age = 0;
            } else if (phase == 8) {
                capture(client);
            } else if (phase == 99) {
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "passed\n");
                finished = true;
                client.options.keyJump.setDown(false);
                client.stop();
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.options.keyJump.setDown(false);
            client.stop();
        }
    }

    private static void initialize(Minecraft client) throws Exception {
        var server = client.getSingleplayerServer();
        var owner = owner(client);
        var manifest = MysticalLifecycle.read(REJOIN ? "mystical-player.json" : "mystical-world.json");
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Lifecycle reused seed/first-client JVM");
        roots = new ArrayList<>();
        if (REJOIN) {
            require(manifest.get("uuid").getAsString().equals(owner.getUUID().toString()), "Rejoin changed owner identity");
            for (int i = 0; i < 4; i++) {
                roots.add(owner.getInventory().getItem(9 + i));
            }
        } else {
            roots.addAll(MysticalLifecycle.worldRoots(server));
        }
        require(MysticalLifecycle.encode(server, roots).equals(manifest.getAsJsonArray("roots")),
                "World/player restart changed complete root/source/purchase data");
        REPORT.append(REJOIN
                      ? "REJOIN same UUID/different JVM: four complete used roots matched before new actions\n"
                      : "RESTART different seed JVM: four complete world roots matched before new actions\n");
        if (!REJOIN) {
            var previous = roots.getFirst();
            var data = ModContent.getMixedBootsData(previous).orElseThrow();
            var rebuilt = MixedBootsRecomposer.recompose(previous, data.left().source(), data.right().source());
            var after = ModContent.getMixedBootsData(rebuilt).orElseThrow();
            require(ItemStack.matches(data.left().source(), after.left().source()) && ItemStack.matches(data.right().source(), after.right().source())
                            && rebuilt.getDamageValue() == previous.getDamageValue() && rebuilt.getCustomName().equals(previous.getCustomName())
                            && MysticalAugmentsCompatibility.selectedIds(rebuilt).equals(MysticalAugmentsCompatibility.selectedIds(previous)),
                    "Recomposition changed provenance/ownership/root state");
            roots.set(0, rebuilt);
            REPORT.append("RECOMPOSE production recomposer preserves used/name state and both original purchase sources\n");
        }
        owner.getInventory().clearContent();
        owner.removeAllEffects();
        owner.setGameMode(GameType.SURVIVAL);
        owner.getFoodData().setFoodLevel(10);
        server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        for (int i = 0; i < 4; i++) {
            owner.getInventory().setItem(9 + i, roots.get(i));
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        owner.connection.teleport(.5, 81, .5, 0, 0);
        prepareHealth(client);
    }

    private static void stash(ServerPlayer owner) {
        var feet = owner.getItemBySlot(EquipmentSlot.FEET);
        for (int i = 0; i < roots.size(); i++) {
            if (feet == roots.get(i)) {
                owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                owner.getInventory().setItem(9 + i, feet);
                break;
            }
        }
        owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        owner.removeAllEffects();
    }

    private static void equip(ServerPlayer owner, ItemStack stack, int index) {
        if (index >= 0) {
            owner.getInventory().setItem(9 + index, ItemStack.EMPTY);
        }
        owner.setItemSlot(EquipmentSlot.FEET, stack);
    }

    private static void prepareHealth(Minecraft client) throws Exception {
        var owner = owner(client);
        stash(owner);
        owner.setNoGravity(true);
        if (healthScene == 0) {
            equip(owner, MysticalAugmentsProbe.item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST"), -1);
        } else if (healthScene < 3) {
            equip(owner, roots.get(healthScene - 1), healthScene - 1);
        }
    }

    private static void prepareJump(Minecraft client) throws Exception {
        var owner = owner(client);
        stash(owner);
        owner.setNoGravity(false);
        owner.setDeltaMovement(Vec3.ZERO);
        if (jumpScene == 0) {
            equip(owner, MysticalAugmentsProbe.item("awakened_supremium_boots", "JUMP_BOOST_III", "NO_FALL_DAMAGE"), -1);
        } else {
            equip(owner, roots.get(jumpScene + 1), jumpScene + 1);
        }
        owner.connection.teleport(.5, 81, .5, 0, 0);
        owner.fallDistance = 0;
    }

    private static void refundsAfterRestart(Minecraft client) {
        var owner = owner(client);
        stash(owner);
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            var before = MysticalAugmentsProbe.delivered(owner);
            var expected = MysticalAugmentsProbe.physicalAugments(ModContent.getMixedBootsData(root).orElseThrow().left().source());
            expected.forEach((id, count) -> before.merge(id, count, Integer::sum));
            equip(owner, root, i);
            require(root.isDamageableItem(), "Persisted break fixture is not damageable");
            root.hurtAndBreak(root.getMaxDamage() + 1, owner, EquipmentSlot.FEET);
            require(root.isEmpty() && MysticalAugmentsProbe.delivered(owner).equals(before),
                    "Restart refund lost/duplicated original left-source purchases");
        }
        require(MysticalAugmentsProbe.delivered(owner).values().stream().mapToInt(Integer::intValue).sum() == 5,
                "Original five purchases were not conserved after actual player/JVM reload");
        REPORT.append("REFUND four actual connected breaks after JVM/player reload return exactly five original purchases across inverse outputs\n");
    }

    private static void capture(Minecraft client) {
        if (captureFailure != null) {
            throw new AssertionError("MA screenshot failed", captureFailure);
        }
        if (captureDone) {
            captureDone = capturePending = false;
            scene = null;
            client.setScreen(null);
            picture++;
            if (picture == visuals.size()) {
                queue(client, () -> {
                    var owner = owner(client);
                    stash(owner);
                    var manifest = new JsonObject();
                    manifest.addProperty("process", ProcessHandle.current().pid());
                    manifest.addProperty("uuid", owner.getUUID().toString());
                    manifest.add("roots", MysticalLifecycle.encode(client.getSingleplayerServer(), roots));
                    NetworkAcceptanceFiles.write("mystical-player.json", manifest.toString());
                    client.getSingleplayerServer().getPlayerList().saveAll();
                    REPORT.append("SAVE actual connected player persists four used roots for same-owner rejoin and later consumption\n");
                }, 99);
                return;
            }
        }
        if (scene == null) {
            scene = new Scene(visuals.get(picture));
            client.setScreen(scene);
            return;
        }
        if (scene.frames < 8 || capturePending) {
            return;
        }
        capturePending = true;
        String name = picture == 0 ? "mystical-native.png" : "mystical-mixed.png";
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), name));
                captureDone = true;
            } catch (Throwable failure) {
                captureFailure = failure;
            }
        });
    }

    private static ServerPlayer owner(Minecraft client) {
        return client.getSingleplayerServer().getPlayerList().getPlayer(MysticalLifecycle.playerId);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < .0001;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void queue(Minecraft client, Action action, int next) {
        job = client.getSingleplayerServer().submit(() -> {
            try {
                action.run();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        nextPhase = next;
        age = 0;
    }

    private static final class Scene extends Screen {
        private final ItemStack root;
        private int frames;

        private Scene(ItemStack root) {
            super(Component.literal("MA production tooltip"));
            this.root = root;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            frames++;
            g.text(font, "Native Mystical Agriculture / mixed source augments", 14, 10, 0xFFFFFFFF, false);
            g.setTooltipForNextFrame(font, root, 18, 45);
        }
    }
}
