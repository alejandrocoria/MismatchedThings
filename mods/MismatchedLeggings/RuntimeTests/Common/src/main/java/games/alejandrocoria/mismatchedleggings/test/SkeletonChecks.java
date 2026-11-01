package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SkeletonChecks {
    private static boolean serverFinished;

    private SkeletonChecks() {
    }

    public static void environment(boolean boots, long products, long cores) {
        require(boots == Boolean.getBoolean("leggings.test.boots") && products == 1 && cores == 1,
                "Wrong product graph or duplicate nested Things");
    }

    public static ItemStack verify() {
        var item = BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID);
        require(item instanceof MixedLeggingsItem, "Product item registration is missing");
        var stack = new ItemStack(item);
        require(stack.getMaxStackSize() == 1 && !stack.has(DataComponents.EQUIPPABLE)
                        && !stack.has(DataComponents.MAX_DAMAGE)
                        && stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                "Bare skeleton item must not provide equipment or inherited stats");
        require(BuiltInRegistries.ITEM.getKey(MismatchedWorkbench.item()).equals(MismatchedWorkbench.ID),
                "Nested Things did not register the shared station");
        String origin = MixedLeggingsItem.class.getProtectionDomain().getCodeSource().getLocation().toString();
        require(origin.contains(".jar"), "Product was loaded from loose classes instead of its artifact: " + origin);
        return stack;
    }

    public static void serverTick(MinecraftServer server) {
        if (!server.isDedicatedServer() || serverFinished || server.getTickCount() < 20) {
            return;
        }
        serverFinished = true;
        try {
            verify();
            if (Boolean.getBoolean("leggings.test.repair")) {
                AnvilRepairChecks.verify(server);
                CraftingRepairChecks.verify(server);
            } else if (Boolean.getBoolean("leggings.test.presentation")) {
                SourceDataChecks.verify(server);
                CompositionChecks.wear(server);
                VanillaLeggingsChecks.verify(server);
            } else if (Boolean.getBoolean("leggings.test.workbench")) {
                WorkbenchChecks.verify(server);
            } else {
                SourceDataChecks.verify(server);
                CompositionChecks.verify(server);
            }
            passed("server");
        } catch (Throwable failure) {
            failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    public static void passed(String phase) throws Exception {
        Files.writeString(report(phase + ".ok"), "packaged leggings " + phase + " checks passed\n");
    }

    public static void failed(String phase, Throwable failure) {
        var trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        try {
            Files.writeString(report(phase + ".failure.txt"), trace.toString());
        } catch (Exception writeFailure) {
            failure.addSuppressed(writeFailure);
        }
        failure.printStackTrace();
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
