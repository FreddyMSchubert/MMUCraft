package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayEntityAccessor {
    @Invoker("setTransformation")
    void mainmod$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void mainmod$setTransformationInterpolationDuration(int duration);

    @Invoker("setTransformationInterpolationDelay")
    void mainmod$setTransformationInterpolationDelay(int delay);

    @Invoker("setBillboardConstraints")
    void mainmod$setBillboardConstraints(Display.BillboardConstraints constraints);

    @Invoker("setViewRange")
    void mainmod$setViewRange(float range);

    @Invoker("setShadowRadius")
    void mainmod$setShadowRadius(float radius);

    @Invoker("setShadowStrength")
    void mainmod$setShadowStrength(float strength);

    @Invoker("setWidth")
    void mainmod$setWidth(float width);

    @Invoker("setHeight")
    void mainmod$setHeight(float height);
}
