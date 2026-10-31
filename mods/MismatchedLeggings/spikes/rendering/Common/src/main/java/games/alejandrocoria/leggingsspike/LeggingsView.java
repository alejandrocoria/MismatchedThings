package games.alejandrocoria.leggingsspike;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;
import java.util.Map;

/** Owns only the waist geometry; animation and the complete chosen leg remain native. */
public final class LeggingsView<S> extends Model<S> {
    private final Model<? super S> source;
    private final WaistHierarchy waist;

    public LeggingsView(Model<? super S> source, boolean left) {
        this(source, new WaistHierarchy(waist(source), left), left);
    }

    private LeggingsView(Model<? super S> source, WaistHierarchy waist, boolean left) {
        super(new ModelPart(List.of(), Map.of("waist", waist.root, "leg",
                left ? humanoid(source).leftLeg : humanoid(source).rightLeg)), source.renderType());
        this.source = source;
        this.waist = waist;
    }

    private static HumanoidModel<?> humanoid(Model<?> model) {
        if (!(model instanceof HumanoidModel<?> humanoid)) {
            throw new IllegalArgumentException("Non-humanoid model");
        }
        return humanoid;
    }

    private static ModelPart waist(Model<?> source) {
        HumanoidModel<?> humanoid = humanoid(source);
        if (!source.root().isEmpty()) {
            throw new IllegalArgumentException("Renderable root geometry is outside this spike");
        }
        return source.root().hasChild("waist") ? source.root().getChild("waist") : humanoid.body;
    }

    @Override
    public void setupAnim(S state) {
        source.setupAnim(state);
        copyTransform(source.root(), root);
        waist.copyLiveTransforms();
    }

    public static void copyTransform(ModelPart from, ModelPart to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
        to.skipDraw = from.skipDraw;
    }
}
