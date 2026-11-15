package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** The debug export establishes merged ownership/order; the live marker establishes final permission. */
public final class SharedRefundWitness {
    private static final String MIXIN = "games.alejandrocoria.mismatchedthings.mixin.compatibility.mysticalagriculture.MysticalRefundMixin";
    private static final String OBSERVER = "games/alejandrocoria/mismatchedthings/internal/compatibility/mysticalagriculture/MysticalRefundObserver";

    private SharedRefundWitness() {
    }

    public static String verify(boolean ready) throws Exception {
        var marker = ItemStack.class.getDeclaredMethod("mismatchedthings$maRefundReady");
        marker.setAccessible(true);
        require(marker.invoke(null).equals(ready), "Shared MA final readiness mismatch");
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/world/item/ItemStack.class"))).accept(node, 0);
        var stages = new HashMap<String, Integer>();
        var priorities = new HashMap<String, Integer>();
        int owned = 0;
        for (var method : node.methods) {
            if (method.visibleAnnotations == null) {
                continue;
            }
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.endsWith("/MixinMerged;") || annotation.values == null) {
                    continue;
                }
                int source = annotation.values.indexOf("mixin") + 1;
                int rank = annotation.values.indexOf("priority") + 1;
                if (source == 0 || rank == 0) {
                    continue;
                }
                String owner = annotation.values.get(source).toString();
                require(!owner.contains("mismatchedboots.mixin.compatibility.mysticalagriculture.MysticalRefundMixin")
                        && !owner.contains("mismatchedleggings.mixin.compatibility.mysticalagriculture.MysticalRefundMixin"), "Legacy product MA hook present");
                priorities.put(owner, (Integer) annotation.values.get(rank));
                if (!owner.equals(MIXIN)) {
                    continue;
                }
                owned++;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(OBSERVER)) {
                        stages.merge(call.name, 1, Integer::sum);
                    }
                }
            }
        }
        int expected = System.getProperty("leggings.test.maOrder", "before").equals("after") ? 900 : 1100;
        require(priorities.get(MIXIN) != null && priorities.get(MIXIN) == expected, "Shared MA relative priority not exercised");
        require(owned == 5, "Expected one marker and exactly four core-owned MA handlers");
        // Mixin's debug exporter runs before the core's final-proof extension. Refused handlers are
        // neutralized later: the live false marker and actual no-refund breaks prove that outcome.
        require(stages.equals(Map.of("capture", 1, "arm", 1, "consumed", 1, "complete", 1)),
                "Merged shared MA stages missing or duplicated: " + stages);
        String jdt = "games.alejandrocoria.mismatchedthings.mixin.compatibility.justdirethings.JustDireRefundMixin";
        if (priorities.containsKey(jdt)) {
            require(priorities.get(jdt) == 1000, "Unexpected JDT consumption priority");
        }
        return "Exported shared MA priority=" + expected + "; JDT=" + priorities.get(jdt) + "; live ready=" + ready + "; pre-publication stages=" + stages
                + "; legacy product MA hooks=0\n";
    }
}
