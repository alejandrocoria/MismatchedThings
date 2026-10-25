package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pinned real recipes/menus only; this gate makes no ability-gameplay claim. */
public final class NeoForgeJustDireSmithingTest {
    private static final List<String> BOOTS = List.of("ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static final List<String> ABILITIES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    private static boolean finished;
    private static int installationRecipes;
    private static int takeCases;

    private NeoForgeJustDireSmithingTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            ServerLevel level = server.overworld();
            ServerPlayer player = new ServerPlayer(server, level,
                                                   new GameProfile(UUID.randomUUID(), "smithing-test"), ClientInformation.createDefault()) {
                @Override
                public GameType gameMode() {
                    return GameType.CREATIVE;
                }
            };
            new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                                             CommonListenerCookie.createInitial(player.getGameProfile(), false));
            verifyMatrix(level, player);
            verifyDisabledAbility(level, player);
            require(installationRecipes == 14 && takeCases == 28, "Incomplete native installation matrix");
            NetworkAcceptanceFiles.write("just-dire-smithing-report.txt",
                                         "4 boots; 5 upgrades; 14 real installation recipes; 6 ineligible tier controls; "
                                                 + "both source orders; root-only installation; eligible union/duplicate rejection; "
                                                 + "28 ordinary/shift-click takes with one addition; blocked take; "
                                                 + "wrong/missing inputs; invalid roots; disabled native ability; unchanged source/root previews\n");
            NetworkAcceptanceFiles.write("just-dire-smithing-success.txt", "neoforge\n");
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("just-dire-smithing", throwable);
        } finally {
            server.halt(false);
        }
    }

    private static void verifyMatrix(ServerLevel level, ServerPlayer player) {
        for (int tier = 0; tier < BOOTS.size(); tier++) {
            for (int ability = 0; ability < ABILITIES.size(); ability++) {
                ItemStack source = NeoForgeJustDireResourcesTest.boots(BOOTS.get(tier).replace("_boots", ""), 2_000);
                source.setDamageValue(7);
                ItemStack addition = new ItemStack(item("upgrade_" + ABILITIES.get(ability)));
                var nativeMenu = menu(level, player, ItemStack.EMPTY, source.copy(), addition.copy());
                ItemStack nativeResult = output(nativeMenu).copy();
                boolean eligible = ability < 2 || ability == 2 && tier >= 1 || ability == 3 && tier >= 2 || ability == 4 && tier == 3;
                require(!nativeResult.isEmpty() == eligible,
                        "Unexpected native tier/ability recipe: " + BOOTS.get(tier) + "/" + ABILITIES.get(ability));
                var pair = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    root.setDamageValue(31);
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Live smithing state"));
                    root.set(DataComponents.REPAIR_COST, 11);
                    ItemStack before = root.copy();
                    var mixedMenu = menu(level, player, ItemStack.EMPTY, root, addition.copy());
                    ItemStack result = output(mixedMenu);
                    require(!result.isEmpty() == eligible, "Mixed tier/ability eligibility differs from native");
                    require(ItemStack.matches(before, root), "Preview changed its mixed input");
                    if (eligible) {
                        MixedBootsData original = data(before);
                        MixedBootsData changed = data(result);
                        require(changed.equals(original), "Root installation changed historical snapshots");
                        require(result.getOrDefault(flag(ABILITIES.get(ability)), false), "Root installation is missing");
                        require(((ResultContainer) mixedMenu.getSlot(SmithingMenu.RESULT_SLOT).container).getRecipeUsed().id()
                                                                                                         .equals(((ResultContainer) nativeMenu.getSlot(
                                                                                                                 SmithingMenu.RESULT_SLOT).container).getRecipeUsed()
                                                                                                                                                     .id()),
                                "Root installation credited a fabricated recipe");
                        require(result.getDamageValue() == 31 && result.get(DataComponents.REPAIR_COST) == 11
                                        && result.getHoverName().equals(root.getHoverName())
                                        && result.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                                                 .equals(before.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)),
                                "Live root state was lost");
                        reject(level, player, ItemStack.EMPTY, result.copy(), addition.copy(), "Repeated installation");
                    }
                    reject(level, player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), root.copy(), addition.copy(), "Wrong template");
                    reject(level, player, ItemStack.EMPTY, root.copy(), ItemStack.EMPTY, "Missing addition");
                    reject(level, player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.DIRT), "Wrong addition");
                }
                if (eligible) {
                    installationRecipes++;
                    ItemStack partial = MixedBootsFactory.createPair(nativeResult, source).primary();
                    require(partial.getOrDefault(flag(ABILITIES.get(ability)), false), "Creation did not import installed union");
                    reject(level, player, ItemStack.EMPTY, partial, addition.copy(), "Imported global installation");
                    verifyTakes(level, player, source, ABILITIES.get(ability), addition);
                }
            }
        }
        reject(level, player, ItemStack.EMPTY, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
               new ItemStack(item("upgrade_stepheight")), "Bare shell");
        ItemStack multiple = MixedBootsFactory.createPair(new ItemStack(item("ferricore_boots")), new ItemStack(Items.IRON_BOOTS)).primary();
        multiple.setCount(2);
        // A real menu clamps an overstack to its slot limit. Check the policy's
        // malformed-count boundary directly instead of mistaking that clamp for acceptance.
        require(MixedBootsSmithingPolicy.evaluate(level, ItemStack.EMPTY, multiple,
                                                  new ItemStack(item("upgrade_stepheight"))).isEmpty(), "Policy accepted multiple mixed bases");
    }

    private static void verifyTakes(ServerLevel level, ServerPlayer player, ItemStack source, String ability, ItemStack addition) {
        int additionCount = Math.min(2, addition.getMaxStackSize());
        for (boolean shiftClick : new boolean[]{false, true}) {
            player.getInventory().clearContent();
            SmithingMenu menu = menu(level, player, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
            ItemStack root = MixedBootsFactory.createPair(source, source).primary();
            MixedBootsData originalSources = data(root);
            require(menu.getSlot(SmithingMenu.BASE_SLOT).mayPlace(root)
                            && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).mayPlace(addition), "Loaded recipe slot properties reject inputs");
            if (shiftClick) {
                menu.getSlot(4).set(root);
                menu.getSlot(5).set(addition.copyWithCount(additionCount));
                menu.quickMoveStack(player, 4);
                menu.quickMoveStack(player, 5);
            } else {
                menu.setCarried(root);
                menu.clicked(SmithingMenu.BASE_SLOT, 0, ContainerInput.PICKUP, player);
                menu.setCarried(addition.copyWithCount(additionCount));
                menu.clicked(SmithingMenu.ADDITIONAL_SLOT, 0, ContainerInput.PICKUP, player);
                require(menu.getCarried().isEmpty(), "Input insertion left an unexpected cursor remainder");
            }
            ItemStack preview = output(menu).copy();
            require(!preview.isEmpty() && preview.getOrDefault(flag(ability), false)
                            && data(preview).equals(originalSources), "Root-only installation changed snapshots or lost its flag");
            if (shiftClick) {
                ItemStack baseBefore = menu.getSlot(SmithingMenu.BASE_SLOT).getItem().copy();
                for (int slot = SmithingMenu.RESULT_SLOT + 1; slot < menu.slots.size(); slot++) {
                    menu.getSlot(slot).set(new ItemStack(Items.STONE, 64));
                }
                require(menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT).isEmpty()
                                && ItemStack.matches(preview, output(menu))
                                && ItemStack.matches(baseBefore, menu.getSlot(SmithingMenu.BASE_SLOT).getItem())
                                && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == additionCount,
                        "Blocked take consumed or lost inputs");
                player.getInventory().clearContent();
            }
            ItemStack taken;
            if (shiftClick) {
                taken = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
            } else {
                menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
                taken = menu.getCarried();
            }
            require(ItemStack.matches(preview, taken), "Extraction differs from preview: " + source + "/" + addition + "; shift=" + shiftClick);
            require(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                            && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                            && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == additionCount - 1
                            && output(menu).isEmpty(), "Installation did not consume exactly one base/addition");
            takeCases++;
        }
    }

    private static void verifyDisabledAbility(ServerLevel level, ServerPlayer player) throws ReflectiveOperationException {
        // Reflection is fixture-only: production remains completely independent of JDT.
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config")
                                           .getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue flag = flags.entrySet().stream()
                                               .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("JUMPBOOST"))
                                               .map(entry -> (ModConfigSpec.BooleanValue) entry.getValue()).findFirst().orElseThrow();
        boolean previous = flag.get();
        try {
            flag.set(false);
            ItemStack source = new ItemStack(item("ferricore_boots"));
            ItemStack addition = new ItemStack(item("upgrade_jumpboost"));
            reject(level, player, ItemStack.EMPTY, source.copy(), addition.copy(), "Disabled native upgrade");
            reject(level, player, ItemStack.EMPTY, MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS)).primary(),
                   addition, "Disabled mixed upgrade");
        } finally {
            flag.set(previous);
        }
    }

    private static void reject(ServerLevel level, ServerPlayer player, ItemStack template, ItemStack base, ItemStack addition, String label) {
        ItemStack before = base.copy();
        require(output(menu(level, player, template, base, addition)).isEmpty(), label + " produced an output");
        require(ItemStack.matches(before, base), label + " mutated the input");
    }

    private static SmithingMenu menu(ServerLevel level, ServerPlayer player, ItemStack template, ItemStack base, ItemStack addition) {
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        return menu;
    }

    private static ItemStack output(SmithingMenu menu) {
        return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
    }

    private static MixedBootsData data(ItemStack stack) {
        return ModContent.getMixedBootsData(stack).orElseThrow();
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Boolean> flag(String ability) {
        return (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", ability + "_upgrade_installed")).orElseThrow().value();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
