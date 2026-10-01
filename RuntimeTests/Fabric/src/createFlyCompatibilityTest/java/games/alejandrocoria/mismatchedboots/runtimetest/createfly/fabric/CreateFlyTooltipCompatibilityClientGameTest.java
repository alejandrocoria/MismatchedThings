package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.client.foundation.item.TooltipModifier;
import com.zurrtum.create.content.equipment.armor.DivingBootsItem;
import games.alejandrocoria.mismatchedboots.compatibility.createfly.CreateFlyMixinPlugin;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class CreateFlyTooltipCompatibilityClientGameTest
        implements FabricClientGameTest {
    private static final String CLIENT_MARKER_PROPERTY =
            "mismatchedboots.test.createFly.clientMarker";
    private static final String LEFT_KEY = "tooltip.mismatchedboots.mixed_boots.left";
    private static final String RIGHT_KEY = "tooltip.mismatchedboots.mixed_boots.right";
    private static final Component FOREIGN_FINAL_HOOK =
            Component.literal("create-fly-compatibility-foreign-final-hook");
    private static final Item REGISTRY_FIXTURE_ITEM = Items.CHAINMAIL_BOOTS;
    private static final Component REGISTRY_FIXTURE_CONTRIBUTION =
            Component.literal("create-fly-compatibility-registry-fixture");

    private static boolean foreignHookRegistered;
    private static int foreignHookInvocations;
    private static ItemStack foreignHookStack;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> assertProductionTooltipGuard());
        context.runOnClient(client -> registerRegistryFixture());
        registerForeignTooltipHook();
        context.runOnClient(CreateFlyTooltipCompatibilityClientGameTest::assertUnattended);
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            characterize(context, AllItems.CARDBOARD_BOOTS, "Cardboard");
            characterize(context, AllItems.COPPER_DIVING_BOOTS, "Copper");
            characterize(context, AllItems.NETHERITE_DIVING_BOOTS, "Netherite");
            context.runOnClient(
                    CreateFlyTooltipCompatibilityClientGameTest::characterizeRegistryDrivenSource);
            context.runOnClient(CreateFlyTooltipCompatibilityClientGameTest::characterizeUnrelatedTooltip);
            context.runOnClient(CreateFlyTooltipCompatibilityClientGameTest::characterizeDivingState);
        }
        completeClientCompatibilityVerification();
    }

    private static void assertProductionTooltipGuard() {
        CreateFlyMixinPlugin plugin = new CreateFlyMixinPlugin();
        require(plugin.shouldApplyMixin(
                        "games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter",
                        "games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly."
                                + "CreateFlyMixedBootsTooltipFormatterMixin"),
                "Production tooltip guard rejected the exact Create Fly artifact");
    }

    private static void characterize(
            ClientGameTestContext context, Item item, String description) {
        @SuppressWarnings("unchecked")
        List<Component>[] normal = new List[1];
        @SuppressWarnings("unchecked")
        List<Component>[] shifted = new List[1];
        context.runOnClient(client -> normal[0] = characterizeMode(
                client, item.getDefaultInstance(), false, description + " normal"));
        context.getInput().holdShift();
        try {
            context.runOnClient(client -> shifted[0] = characterizeMode(
                    client, item.getDefaultInstance(), true, description + " Shift"));
        } finally {
            context.getInput().releaseShift();
        }
        require(!normal[0].equals(shifted[0]),
                description + " Create tooltip did not react to Shift");
        context.runOnClient(client -> assertHiddenSourceTooltip(
                client, item.getDefaultInstance(), normal[0], description));
    }

    private static List<Component> characterizeMode(
            Minecraft client, ItemStack source, boolean shiftDown, String description) {
        require(client.player != null, "Create Fly tooltip compatibility test has no client player");
        require(client.hasShiftDown() == shiftDown,
                description + " did not receive the expected client Shift state");
        require(TooltipModifier.REGISTRY.get(Items.IRON_BOOTS) == null,
                "Create registered a tooltip modifier for unrelated iron boots");
        TooltipModifier modifier = TooltipModifier.REGISTRY.get(source.getItem());
        require(modifier != null, description + " Boots have no Create tooltip modifier");

        List<Component> contribution = contribution(modifier, client);
        require(!nonBlank(contribution).isEmpty(),
                description + " Create tooltip contribution is empty");
        assertOrdinaryTooltip(client, source, contribution, description);

        ItemStack sourceBefore = source.copy();
        MixedBootsPair pair = MixedBootsFactory.createPair(
                source, Items.IRON_BOOTS.getDefaultInstance());
        assertMixedContribution(client, pair.primary(), source, contribution, true,
                description + " left source");
        assertMixedContribution(client, pair.inverse(), source, contribution, false,
                description + " right source");
        require(ItemStack.matches(sourceBefore, source),
                description + " source changed during tooltip compatibility verification");
        return contribution;
    }

    private static List<Component> contribution(
            TooltipModifier modifier, Minecraft client) {
        ArrayList<Component> lines = new ArrayList<>();
        Component itemNamePlaceholder = Component.literal("create-fly-compatibility");
        lines.add(itemNamePlaceholder);
        modifier.modify(lines, client.player);
        require(lines.remove(itemNamePlaceholder),
                "Create tooltip modifier removed the item-name line");
        return List.copyOf(lines);
    }

    private static void assertOrdinaryTooltip(
            Minecraft client,
            ItemStack source,
            List<Component> expectedContribution,
            String description) {
        resetForeignHook();
        List<Component> lines = tooltip(client, source);
        for (Component expected : nonBlank(expectedContribution)) {
            require(lines.contains(expected),
                    description + " ordinary tooltip omitted Create line " + expected);
        }
        require(foreignHookInvocations == 1 && foreignHookStack == source,
                description + " ordinary tooltip did not run the foreign final hook exactly once");
        require(count(lines, FOREIGN_FINAL_HOOK) == 1,
                description + " ordinary tooltip omitted or duplicated the foreign final hook");
        require(indexOfSubsequence(lines, expectedContribution, 0, lines.size()) >= 0,
                description + " ordinary tooltip did not keep Create's contribution together");

        // A generic nested getTooltipLines path cannot isolate Create: it also executes and
        // captures every unrelated final tooltip hook registered for the source stack.
        require(lines.contains(FOREIGN_FINAL_HOOK),
                description + " generic final-hook candidate unexpectedly isolated Create");
    }

    private static void assertMixedContribution(
            Minecraft client,
            ItemStack mixed,
            ItemStack expectedSource,
            List<Component> contribution,
            boolean sourceOnLeft,
            String description) {
        ItemStack rootBefore = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);
        resetForeignHook();
        List<Component> lines = tooltip(client, mixed);

        require(lines.stream().anyMatch(
                        line -> line.getString().contains(expectedSource.getHoverName().getString())),
                description + " tooltip omitted the source identity");
        require(foreignHookInvocations == 1 && foreignHookStack == mixed,
                description + " recursively executed a foreign final tooltip hook");
        require(count(lines, FOREIGN_FINAL_HOOK) == 1,
                description + " omitted or duplicated the root foreign final hook");

        int left = indexOfTranslation(lines, LEFT_KEY);
        int right = indexOfTranslation(lines, RIGHT_KEY);
        require(left >= 0 && right > left,
                description + " lost the established source section order");
        int sectionStart = sourceOnLeft ? left : right;
        int sectionEnd = sourceOnLeft ? right : lines.size();
        int contributionStart = indexOfSubsequence(
                lines, contribution, sectionStart + 1, sectionEnd);
        require(contributionStart == sectionStart + 1,
                description + " did not place Create's contribution immediately after the source name");
        require(countSubsequence(lines, contribution) == 1,
                description + " duplicated Create's contribution");
        int foreignIndex = lines.indexOf(FOREIGN_FINAL_HOOK);
        require(foreignIndex > right
                        && (foreignIndex < sectionStart || foreignIndex >= contributionStart + contribution.size()),
                description + " leaked a foreign final hook into the source section");
        require(ItemStack.matches(rootBefore, mixed),
                description + " tooltip changed root item data");
        require(sourcesBefore.equals(mixedData(mixed)),
                description + " tooltip changed stored source snapshots");
    }

    private static void assertHiddenSourceTooltip(
            Minecraft client,
            ItemStack source,
            List<Component> contribution,
            String description) {
        source.set(
                DataComponents.TOOLTIP_DISPLAY,
                new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        MixedBootsPair pair = MixedBootsFactory.createPair(
                source, Items.IRON_BOOTS.getDefaultInstance());
        ItemStack mixed = pair.primary();
        ItemStack rootBefore = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);

        resetForeignHook();
        List<Component> lines = tooltip(client, mixed);
        require(indexOfTranslation(lines, LEFT_KEY) >= 0,
                description + " hidden source lost its identity line");
        require(countSubsequence(lines, contribution) == 0,
                description + " hidden source leaked Create's contribution");
        require(foreignHookInvocations == 1 && foreignHookStack == mixed,
                description + " hidden source recursively ran a final tooltip hook");
        require(ItemStack.matches(rootBefore, mixed) && sourcesBefore.equals(mixedData(mixed)),
                description + " hidden source tooltip changed root or source data");
    }

    private static void characterizeUnrelatedTooltip(Minecraft client) {
        MixedBootsPair pair = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        ItemStack mixed = pair.primary();
        ItemStack before = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);

        require(TooltipModifier.REGISTRY.get(Items.IRON_BOOTS) == null
                        && TooltipModifier.REGISTRY.get(Items.GOLDEN_BOOTS) == null,
                "Create registered a tooltip modifier for the unrelated control sources");
        resetForeignHook();
        List<Component> lines = tooltip(client, mixed);
        require(indexOfTranslation(lines, LEFT_KEY) >= 0
                        && indexOfTranslation(lines, RIGHT_KEY) > indexOfTranslation(lines, LEFT_KEY),
                "Unrelated Mismatched tooltip lost its source sections");
        require(foreignHookInvocations == 1 && foreignHookStack == mixed
                        && count(lines, FOREIGN_FINAL_HOOK) == 1,
                "Unrelated Mismatched tooltip recursively ran its final hook");
        require(ItemStack.matches(before, mixed) && sourcesBefore.equals(mixedData(mixed)),
                "Unrelated Mismatched tooltip changed root or source data");
    }

    private static void characterizeRegistryDrivenSource(Minecraft client) {
        ItemStack source = REGISTRY_FIXTURE_ITEM.getDefaultInstance();
        TooltipModifier modifier = TooltipModifier.REGISTRY.get(REGISTRY_FIXTURE_ITEM);
        require(modifier != null, "Synthetic registry fixture was not registered");
        List<Component> contribution = contribution(modifier, client);
        require(contribution.equals(List.of(REGISTRY_FIXTURE_CONTRIBUTION)),
                "Synthetic registry fixture produced an unexpected contribution");

        assertOrdinaryTooltip(client, source, contribution, "Registry-driven control");
        MixedBootsPair pair = MixedBootsFactory.createPair(
                source, Items.IRON_BOOTS.getDefaultInstance());
        assertMixedContribution(client, pair.primary(), source, contribution, true,
                "Registry-driven left source");
        assertMixedContribution(client, pair.inverse(), source, contribution, false,
                "Registry-driven right source");
    }

    private static List<Component> tooltip(Minecraft client, ItemStack stack) {
        return stack.getTooltipLines(
                Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
    }

    private static List<Component> nonBlank(List<Component> lines) {
        return lines.stream().filter(line -> !line.getString().isBlank()).toList();
    }

    private static int indexOfSubsequence(
            List<Component> lines,
            List<Component> expected,
            int fromIndex,
            int toIndex) {
        int lastStart = toIndex - expected.size();
        for (int index = fromIndex; index <= lastStart; index++) {
            if (lines.subList(index, index + expected.size()).equals(expected)) {
                return index;
            }
        }
        return -1;
    }

    private static int countSubsequence(List<Component> lines, List<Component> expected) {
        int count = 0;
        for (int index = 0; index + expected.size() <= lines.size(); index++) {
            if (lines.subList(index, index + expected.size()).equals(expected)) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfTranslation(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)) {
                return true;
            }
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream()
                .anyMatch(sibling -> containsTranslation(sibling, key));
    }

    private static int count(List<Component> lines, Component expected) {
        return (int) lines.stream().filter(expected::equals).count();
    }

    private static MixedBootsData mixedData(ItemStack stack) {
        return ModContent.getMixedBootsData(stack)
                .orElseThrow(() -> new AssertionError("Missing mixed-boots source data"));
    }

    private static void registerForeignTooltipHook() {
        if (foreignHookRegistered) {
            return;
        }
        foreignHookRegistered = true;
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            Item item = stack.getItem();
            if (TooltipModifier.REGISTRY.get(item) == null
                    && !ModContent.isMixedBoots(stack)) {
                return;
            }
            foreignHookInvocations++;
            foreignHookStack = stack;
            lines.add(FOREIGN_FINAL_HOOK);
        });
    }

    private static void registerRegistryFixture() {
        require(TooltipModifier.REGISTRY.get(REGISTRY_FIXTURE_ITEM) == null,
                "Create already registered the synthetic tooltip fixture item");
        TooltipModifier.REGISTRY.register(
                REGISTRY_FIXTURE_ITEM,
                (lines, player) -> lines.add(1, REGISTRY_FIXTURE_CONTRIBUTION));
    }

    private static void resetForeignHook() {
        foreignHookInvocations = 0;
        foreignHookStack = null;
    }

    private static void assertUnattended(Minecraft client) {
        require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F,
                "Create Fly compatibility client master volume is not muted");
        if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")) {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE)
                            == GLFW.GLFW_FALSE,
                    "Create Fly compatibility client window is visible");
        }
    }

    private static void characterizeDivingState(Minecraft client) {
        require(client.player != null, "Create Fly Diving compatibility test has no client player");
        ItemStack previous = client.player.getItemBySlot(EquipmentSlot.FEET).copy();
        MixedBootsPair pair = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance());
        try {
            assertClientDivingState(client, pair.primary(), "primary");
            assertClientDivingState(client, pair.inverse(), "inverse");
        } finally {
            client.player.setItemSlot(EquipmentSlot.FEET, previous);
            DivingBootsItem.accelerateDescentUnderwater(client.player);
        }
    }

    private static void assertClientDivingState(
            Minecraft client, ItemStack mixed, String orientation) {
        ItemStack rootBefore = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);
        client.player.setItemSlot(EquipmentSlot.FEET, mixed);
        DivingBootsItem.accelerateDescentUnderwater(client.player);
        require(AllSynchedDatas.HEAVY_BOOTS.get(client.player),
                orientation + " client Mismatched Diving Boots did not set HEAVY_BOOTS");
        require(DivingBootsItem.getWornItem(client.player).getItem()
                        == AllItems.NETHERITE_DIVING_BOOTS,
                orientation + " client Copper/Netherite pair did not prioritize Netherite");

        client.player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        DivingBootsItem.accelerateDescentUnderwater(client.player);
        require(!AllSynchedDatas.HEAVY_BOOTS.get(client.player),
                orientation + " client HEAVY_BOOTS state did not clear after removal");
        require(ItemStack.matches(rootBefore, mixed),
                orientation + " client Diving compatibility check changed root item data");
        require(sourcesBefore.equals(mixedData(mixed)),
                orientation + " client Diving compatibility check changed source snapshots");
    }

    private static void completeClientCompatibilityVerification() {
        String marker = System.getProperty(CLIENT_MARKER_PROPERTY);
        require(marker != null && !marker.isBlank(),
                "Create Fly client compatibility marker is not configured");
        try {
            Files.writeString(
                    Path.of(marker),
                    "create-fly-compatibility-passed\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(
                    "Could not write the Create Fly client compatibility marker", exception);
        }
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
