package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Paid native recipes, with independent input/output and resource-preservation controls. */
final class AllTheModiumWorkstationChecks {
    private AllTheModiumWorkstationChecks() {
    }

    static void verify(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        ItemStack input = new ItemStack(Items.NETHERITE_LEGGINGS);
        for (String tier : List.of("allthemodium", "vibranium", "unobtainium")) {
            var expected = nativeItem(tier + "_leggings");
            var template = nativeItem(tier + "_upgrade_smithing_template");
            var addition = nativeItem(tier + "_ingot");
            var nativeOutput = paid(player, input, template, addition, false);
            require(nativeOutput.is(expected.getItem()), "Native upgrade control did not select " + tier);

            var ordinary = new ItemStack(Items.IRON_LEGGINGS);
            var pair = MixedLeggingsFactory.createPair(input, ordinary, CompositionRules.defaults());
            for (boolean inverse : List.of(false, true)) {
                var root = SmithingChecks.decorate(server, inverse ? pair.inverse() : pair.primary());
                var before = root.copy();
                var taken = paid(player, root, template, addition, inverse);
                SmithingChecks.preserved(before, taken);
                var data = MixedLeggingsStructure.validData(taken).orElseThrow();
                var changed = inverse ? data.right().source() : data.left().source();
                var unchanged = inverse ? data.left().source() : data.right().source();
                require(changed.is(nativeOutput.getItem()) && ItemStack.matches(unchanged, ordinary),
                        "Native upgrade changed the wrong source or damaged its partner");
                require(!taken.has(DataComponents.UNBREAKABLE) && !taken.has(DataComponents.DAMAGE_RESISTANT),
                        "Native smithing bypassed component composition policies");
            }
            policies(expected, ordinary);
            trims(server, player, expected, ordinary);
            input = expected;
        }
    }

    private static void trims(MinecraftServer server, ServerPlayer player, ItemStack source, ItemStack ordinary) {
        var template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
        for (var addition : List.of(new ItemStack(Items.DIAMOND), nativeItem("allthemodium_ingot"))) {
            var nativeTrim = paid(player, source, template, addition, false).get(DataComponents.TRIM);
            require(nativeTrim != null, "Native ordinary/custom trim control failed");
            var pair = MixedLeggingsFactory.createPair(source, ordinary, CompositionRules.defaults());
            for (var root : List.of(pair.primary(), pair.inverse())) {
                SmithingChecks.decorate(server, root);
                var taken = paid(player, root, template, addition, true);
                SmithingChecks.preserved(root, taken);
                var data = MixedLeggingsStructure.validData(taken).orElseThrow();
                require(nativeTrim.equals(data.left().source().get(DataComponents.TRIM))
                                && nativeTrim.equals(data.right().source().get(DataComponents.TRIM)),
                        "Paid ordinary/custom trim did not reach both sources");
            }
        }
    }

    private static ItemStack paid(ServerPlayer player, ItemStack input, ItemStack template, ItemStack addition, boolean shift) {
        var before = input.copy();
        player.getInventory().clearContent();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        require(menu.getSlot(1).mayPlace(input), "Native smithing base set rejected the input");
        menu.getSlot(0).set(template.copyWithCount(2));
        menu.getSlot(1).set(input.copy());
        menu.getSlot(2).set(addition.copyWithCount(2));
        menu.createResult();
        var preview = menu.getSlot(3).getItem().copy();
        require(!preview.isEmpty(), "Native recipe produced no preview: " + input + " + " + addition);
        var taken = shift ? menu.quickMoveStack(player, 3) : menu.getSlot(3).safeTake(1, 1, player);
        require(ItemStack.matches(preview, taken) && !taken.isEmpty() && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1
                        && menu.getSlot(3).safeTake(1, 1, player).isEmpty(),
                "Native recipe payment, extraction or repeated take failed");
        require(ItemStack.matches(input, before), "Smithing mutated its caller's input");
        return taken;
    }

    private static void policies(ItemStack nativeStack, ItemStack ordinary) {
        var pair = MixedLeggingsFactory.createPair(nativeStack, ordinary, CompositionRules.defaults());
        int maximum = (int) (((long) nativeStack.getMaxDamage() + ordinary.getMaxDamage()) / 2L);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(!root.has(DataComponents.UNBREAKABLE) && !root.has(DataComponents.DAMAGE_RESISTANT)
                            && root.getMaxDamage() == maximum && root.isDamageableItem(),
                    "Single native source lost ordinary average durability/component policies");
        }
        var both = MixedLeggingsFactory.createPair(nativeStack, nativeStack, CompositionRules.defaults());
        for (var root : List.of(both.primary(), both.inverse())) {
            require(root.has(DataComponents.UNBREAKABLE) && root.has(DataComponents.DAMAGE_RESISTANT)
                            && root.getMaxDamage() == nativeStack.getMaxDamage() && !root.isDamageableItem(),
                    "Agreeing native inputs lost their composed Unbreakable/fire-resistance flags");
        }
    }

    static ItemStack nativeItem(String name) {
        var id = Identifier.fromNamespaceAndPath("allthemodium", name);
        return new ItemStack(BuiltInRegistries.ITEM.get(id).orElseThrow().value());
    }
}
