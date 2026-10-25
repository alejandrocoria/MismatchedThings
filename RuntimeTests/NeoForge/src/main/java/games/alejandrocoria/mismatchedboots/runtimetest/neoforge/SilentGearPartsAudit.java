package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsRepairRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Diagnostic characterization, not a prototype adapter or a promised part-editing policy. */
final class SilentGearPartsAudit {
    private SilentGearPartsAudit() {
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack iron = api.boot("iron");
        var roots = NeoForgeSilentGearTest.workbench(player, iron, new ItemStack(Items.DIAMOND_BOOTS));
        ItemStack root = roots.getFirst();
        Object swap = recipe("GearPartSwapRecipe");
        ItemStack lining = api.part("lining", "wool");
        var input = CraftingInput.of(2, 1, List.of(iron.copy(), lining.copy()));
        require(matches(swap, input, player), "Native lining control rejected");
        ItemStack lined = assemble(swap, input);
        require(!ItemStack.matches(iron, lined), "Native lining control unchanged");
        for (ItemStack mixed : roots) {
            require(!matches(swap, CraftingInput.of(2, 1, List.of(mixed.copy(), lining.copy())), player),
                    "Native swap unexpectedly admits mixed");
            var menu = crafting(player, mixed.copy(), lining.copy());
            require(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty(), "Mixed lining got a real crafting output");
        }
        var paid = crafting(player, iron.copy(), lining.copy());
        ItemStack taken = paid.getSlot(InventoryMenu.RESULT_SLOT).safeTake(1, 1, player);
        require(ItemStack.matches(taken, lined) && paid.getInputGridSlots().stream().allMatch(s -> s.getItem().isEmpty()),
                "Native lining menu did not pay for changed output");
        report.append("PART SWAP: native paid lining changes construction; both mixed orientations have no crafting result\n");
        for (ItemStack addition : List.of(api.part("boot_plates", "diamond"), api.part("tip", "diamond"),
                                          api.part("coating", "gold"), item("magnetic_upgrade"), item("red_card_upgrade"))) {
            controls(player, "GearPartSwapRecipe", iron, root, List.of(addition), report);
        }

        // Select LINING exactly as the native cycling tool would, without installing any root support.
        ItemStack kit = item("mod_kit");
        Object liningType = ((Supplier<?>) Class.forName("net.silentchaos512.gear.setup.gear.PartTypes")
                                                .getField("LINING").get(null)).get();
        kit.set(NeoForgeSilentGearTest.NativeApi.component("PART_TYPE"), liningType);
        ItemStack linedRoot = NeoForgeSilentGearTest.workbench(player, lined, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        controls(player, "modkit.ModKitRemovePartRecipe", lined, linedRoot, List.of(kit), report);
        controls(player, "modkit.ModKitPaintPartRecipe", lined, linedRoot, List.of(kit, new ItemStack(Items.RED_DYE)), report);
        controls(player, "QuickPaintRecipe", iron, root, List.of(new ItemStack(Items.RED_DYE)), report);
        ItemStack damaged = iron.copy();
        damaged.setDamageValue(40);
        ItemStack damagedRoot = root.copy();
        damagedRoot.setDamageValue(40);
        controls(player, "QuickRepairRecipe", damaged, damagedRoot,
                 List.of(item("crude_repair_kit"), new ItemStack(Items.IRON_INGOT)), report);

        for (ItemStack mixed : roots) {
            smithing(player, mixed, item("magnetic_upgrade"), ItemStack.EMPTY, false, report);
            smithing(player, mixed, new ItemStack(Items.GOLD_INGOT), item("coating_smithing_template"), false, report);
        }
        smithing(player, iron, item("magnetic_upgrade"), ItemStack.EMPTY, true, report);
        smithing(player, iron, new ItemStack(Items.GOLD_INGOT), item("coating_smithing_template"), true, report);

        // Mechanism-only control: a source edit is NOT a currently admitted player transaction.
        ItemStack before = root.copy();
        root.setDamageValue(37);
        before.setDamageValue(37);
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        ItemStack recomposed = MixedBootsRecomposer.recompose(root, lined, data.right().source());
        require(recomposed.getDamageValue() == 37 && ItemStack.matches(root, before), "Recomposition changed input/damage");
        require(ItemStack.matches(ModContent.getMixedBootsData(recomposed).orElseThrow().left().source(), lined),
                "Recomposition discarded edited source");
        report.append("RECOMPOSITION: nonoxidizing lining edit supported as mechanism; absolute damage=37 preserved, input unchanged\n");
        ItemStack copper = api.boot("copper");
        ItemStack copperRoot = NeoForgeSilentGearTest.workbench(player, copper, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        ItemStack copperBefore = copperRoot.copy();
        ItemStack copperLined = assemble(swap, CraftingInput.of(2, 1, List.of(copper.copy(), lining.copy())));
        boolean refused = false;
        try {
            MixedBootsRecomposer.recompose(copperRoot, copperLined,
                                           ModContent.getMixedBootsData(copperRoot).orElseThrow().right().source());
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("oxidation reset"), "Unexpected recompose rejection: " + expected);
            refused = true;
        }
        require(refused && ItemStack.matches(copperRoot, copperBefore), "Copper construction change not safely refused");
        report.append("RECOMPOSITION: copper+lining explicitly refused (oxidation reset undefined); root/source/state unchanged\n");

        var repair = new MixedBootsRepairRecipe();
        require(!repair.matches(CraftingInput.of(2, 1, List.of(damagedRoot, damaged)), player.level()),
                "SG donor unexpectedly supports crafting combine repair");
        require(repair.matches(CraftingInput.of(2, 1, List.of(damagedRoot, new ItemStack(Items.DIAMOND_BOOTS))), player.level()),
                "Vanilla donor control failed");
        report.append("REPAIR: mixed crafting repair rejects SG donor's no-combine flag; vanilla diamond donor accepted\n");
        var nativeRepair = anvil(player, damaged, new ItemStack(Items.IRON_INGOT));
        require(!nativeRepair.isEmpty() && nativeRepair.getDamageValue() < damaged.getDamageValue(), "Native anvil material control failed");
        report.append("ANVIL native iron ingot: native material repair preview\n");
        for (String upgrade : List.of("magnetic_upgrade", "red_card_upgrade")) {
            ItemStack nativeResult = anvil(player, iron, item(upgrade));
            require(!nativeResult.isEmpty() && !ItemStack.matches(nativeResult, iron), "Native anvil upgrade failed: " + upgrade);
            require(anvil(player, root, item(upgrade)).isEmpty(), "Mixed anvil upgrade unexpectedly admitted");
            report.append("ANVIL ").append(upgrade).append(": native changed upgrade preview; mixed rejected\n");
        }
        for (ItemStack donor : List.of(api.boot("leather"), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND))) {
            ItemStack result = anvil(player, damagedRoot, donor);
            boolean accepted = !donor.is(Items.IRON_INGOT);
            require(!result.isEmpty() == accepted, "Unexpected anvil admission for " + donor);
            if (accepted) {
                require(result.getDamageValue() < damagedRoot.getDamageValue()
                                && ModContent.getMixedBootsData(result).equals(ModContent.getMixedBootsData(damagedRoot)),
                        "Anvil donor changed source construction or did not repair");
            }
            report.append("ANVIL ").append(BuiltInRegistries.ITEM.getKey(donor.getItem()))
                  .append(accepted ? ": repair preview; source construction unchanged\n" : ": no repair preview\n");
        }
        report.append("TAGS native=").append(iron.typeHolder().tags().map(t -> t.location().toString()).sorted().toList())
              .append(" root=").append(root.typeHolder().tags().map(t -> t.location().toString()).sorted().toList()).append('\n');
    }

    private static void controls(ServerPlayer player, String name, ItemStack nativeGear, ItemStack root,
                                 List<ItemStack> additions, StringBuilder report) throws Exception {
        Object recipe = recipe(name);
        var inputs = new java.util.ArrayList<ItemStack>();
        inputs.add(nativeGear.copy());
        additions.forEach(s -> inputs.add(s.copy()));
        var input = CraftingInput.of(inputs.size(), 1, inputs);
        require(matches(recipe, input, player), "Native control rejected: " + name);
        ItemStack result = assemble(recipe, input);
        require(!result.isEmpty() && !ItemStack.matches(nativeGear, result), "Native control unchanged: " + name);
        if (name.equals("QuickRepairRecipe")) {
            require(result.getDamageValue() < nativeGear.getDamageValue(), "Native kit control changed metadata without repairing");
        }
        inputs.set(0, root.copy());
        require(!matches(recipe, CraftingInput.of(inputs.size(), 1, inputs), player), "Mixed unexpectedly admitted: " + name);
        report.append("CRAFT ").append(name).append(' ')
              .append(additions.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList())
              .append(": native matches/changes, mixed rejected\n");
    }

    private static InventoryMenu crafting(ServerPlayer player, ItemStack base, ItemStack addition) {
        var menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(base);
        menu.getInputGridSlots().get(3).set(addition);
        return menu;
    }

    private static ItemStack anvil(ServerPlayer player, ItemStack base, ItemStack addition) {
        var menu = new AnvilMenu(1, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(base.copy());
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition.copy());
        return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
    }

    private static void smithing(ServerPlayer player, ItemStack base, ItemStack addition, ItemStack template,
                                 boolean nativeControl, StringBuilder report) {
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template.copy());
        menu.getSlot(SmithingMenu.BASE_SLOT).set(base.copy());
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition.copy());
        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        if (nativeControl) {
            require(ItemStack.matches(preview, base), "Native smithing no-op not reproduced: " + addition);
            menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), base) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                            && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                            && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Native no-op payment differs");
            report.append("SMITH native ").append(BuiltInRegistries.ITEM.getKey(addition.getItem()))
                  .append(": unchanged output; base/template/addition consumed\n");
        } else {
            require(preview.isEmpty(), "Mixed no-op smithing unexpectedly admitted");
            menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getSlot(SmithingMenu.BASE_SLOT).getItem(), base)
                            && ItemStack.matches(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem(), addition)
                            && ItemStack.matches(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(), template)
                            && menu.getCarried().isEmpty(), "Rejected mixed smithing consumed inputs");
            report.append("SMITH mixed ").append(BuiltInRegistries.ITEM.getKey(addition.getItem())).append(": no result, inputs unchanged\n");
        }
    }

    private static Object recipe(String name) throws Exception {
        return Class.forName("net.silentchaos512.gear.crafting.recipe." + name).getField("INSTANCE").get(null);
    }

    private static boolean matches(Object recipe, CraftingInput input, ServerPlayer player) throws Exception {
        return (boolean) recipe.getClass().getMethod("matches", CraftingInput.class, Level.class).invoke(recipe, input, player.level());
    }

    private static ItemStack assemble(Object recipe, CraftingInput input) throws Exception {
        return (ItemStack) recipe.getClass().getMethod("assemble", CraftingInput.class).invoke(recipe, input);
    }

    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("silentgear", name)).orElseThrow().value());
    }
}
