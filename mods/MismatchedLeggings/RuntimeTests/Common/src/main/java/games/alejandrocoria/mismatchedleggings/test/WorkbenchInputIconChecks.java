package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Client-local probe menus leave the real connection's transaction and inventory untouched. */
public final class WorkbenchInputIconChecks {
    private WorkbenchInputIconChecks() {
    }

    public static void verify(Minecraft client) throws ReflectiveOperationException {
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        Identifier bootsIcon = InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
        Identifier leggingsIcon = InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
        List<Identifier> all = boots ? List.of(bootsIcon, leggingsIcon) : List.of(leggingsIcon);
        for (Identifier icon : all) {
            require(client.getResourceManager().getResource(Identifier.fromNamespaceAndPath(
                    icon.getNamespace(), "textures/gui/sprites/" + icon.getPath() + ".png")).isPresent(),
                    "Input icon resource is missing: " + icon);
        }

        var menu = new WorkbenchMenu(600, client.player.getInventory());
        var screen = new WorkbenchScreen(menu, client.player.getInventory(), Component.literal("Icon probe"));
        expect(screen, all);
        advance(screen, all);

        menu.getSlot(0).set(new ItemStack(Items.IRON_LEGGINGS));
        advance(screen, List.of(leggingsIcon));
        require(menu.getSlot(0).getItem().getCount() == 1, "Icon query mutated the left source");
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(new ItemStack(Items.IRON_LEGGINGS));
        advance(screen, List.of(leggingsIcon));
        require(menu.getSlot(1).getItem().getCount() == 1, "Icon query mutated the right source");

        menu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        menu.getSlot(1).set(ItemStack.EMPTY);
        advance(screen, boots ? List.of(bootsIcon) : List.of());
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(new ItemStack(Items.IRON_BOOTS));
        advance(screen, boots ? List.of(bootsIcon) : List.of());

        // A forced unsupported input must not advertise another product as a valid partner.
        menu.getSlot(1).set(new ItemStack(Items.STONE));
        advance(screen, List.of());
        menu.getSlot(1).set(ItemStack.EMPTY);
        advance(screen, all);

        var field = WorkbenchMenu.class.getDeclaredField("committed");
        field.setAccessible(true);
        var committed = (DataSlot) field.get(menu);
        committed.set(1);
        advance(screen, List.of());
        committed.set(0);
        advance(screen, all);
    }

    private static void advance(WorkbenchScreen screen, List<Identifier> expected) throws ReflectiveOperationException {
        // Span multiple native icon changes, including after removing/replacing an input.
        for (int tick = 0; tick < 65; tick++) {
            expect(screen, expected);
        }
    }

    private static void expect(WorkbenchScreen screen, List<Identifier> expected)
            throws ReflectiveOperationException {
        screen.containerTick();
        CyclingSlotBackground left = background(screen, "leftInputIcon");
        CyclingSlotBackground right = background(screen, "rightInputIcon");
        var icons = CyclingSlotBackground.class.getDeclaredField("icons");
        icons.setAccessible(true);
        require(icons.get(left).equals(expected), "Wrong left empty-input icons; expected " + expected);
        require(icons.get(right).equals(expected), "Wrong right empty-input icons; expected " + expected);
        for (String name : List.of("tick", "iconIndex")) {
            var field = CyclingSlotBackground.class.getDeclaredField(name);
            field.setAccessible(true);
            require(field.getInt(left) == field.getInt(right), "Input icon animations drifted: " + name);
        }
    }

    private static CyclingSlotBackground background(WorkbenchScreen screen, String name) throws ReflectiveOperationException {
        var field = WorkbenchScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return (CyclingSlotBackground) field.get(screen);
    }
}
