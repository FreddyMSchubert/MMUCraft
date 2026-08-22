package uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing;

import com.mojang.math.Transformation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.httpsmmuminecraftsociety.mainmod.fishing.FishingPersonality;

final class AnimalCrossingFishShadowDisplay {
	static final int ARRIVAL_TICKS = 18;
	static final int SCURRY_TICKS = 16;

	private AnimalCrossingFishShadowDisplay() {}

	record AnimationState(
			AnimalCrossingFishingPhase phase,
			double orbitDegrees,
			double fishDistance,
			int arrivalTicks,
			int pauseTicks,
			int animationTicks,
			int catchAnimationTicks
	) {}

    static Display.ItemDisplay create(ServerLevel level, FishingPersonality personality) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setInvisible(false);

        ItemStack shadowStack = new ItemStack(Items.PAPER);
        shadowStack.set(DataComponents.ITEM_MODEL, Identifier.parse(personality.fishShape()));
        ((ItemDisplayEntityAccessor) display).mainmod$setItemStack(shadowStack);
        ((ItemDisplayEntityAccessor) display).mainmod$setItemTransform(ItemDisplayContext.FIXED);

        DisplayEntityAccessor accessor = (DisplayEntityAccessor) display;
        accessor.mainmod$setBillboardConstraints(Display.BillboardConstraints.FIXED);
        accessor.mainmod$setTransformationInterpolationDuration(0);
        accessor.mainmod$setTransformationInterpolationDelay(0);
        accessor.mainmod$setViewRange(32.0F);
        accessor.mainmod$setShadowRadius(0.0F);
        accessor.mainmod$setShadowStrength(0.0F);
        accessor.mainmod$setWidth(1.6F);
        accessor.mainmod$setHeight(1.6F);
        return display;
    }

	static void position(
			FishingHook hook,
			Display.ItemDisplay display,
			FishingPersonality personality,
			AnimationState animation
	) {
		double radians = Math.toRadians(animation.orbitDegrees());
		double x = hook.getX() + Math.sin(radians) * animation.fishDistance();
		double z = hook.getZ() + Math.cos(radians) * animation.fishDistance();
		double y = hook.getY() + 0.035D;
		if (animation.phase() == AnimalCrossingFishingPhase.ARRIVING) {
			y -= (animation.arrivalTicks() / (double) ARRIVAL_TICKS) * 0.95D;
		} else if (animation.phase() == AnimalCrossingFishingPhase.SCURRYING) {
			y -= (SCURRY_TICKS - animation.pauseTicks()) * 0.045D;
		}

		display.setPos(x, y, z);
		display.setYRot(0.0F);
		display.setXRot(0.0F);

		float pulse = animation.phase() == AnimalCrossingFishingPhase.APPROACHING
				&& animation.pauseTicks() <= 0
				? 1.0F
				: 1.0F + (float) Math.sin(animation.animationTicks() * 0.24D) * 0.035F;
		if (animation.phase() == AnimalCrossingFishingPhase.ARRIVING) {
			pulse *= Math.max(0.08F, (ARRIVAL_TICKS - animation.arrivalTicks()) / (float) ARRIVAL_TICKS);
		} else if (animation.phase() == AnimalCrossingFishingPhase.SCURRYING) {
			pulse *= Math.max(0.08F, animation.pauseTicks() / (float) SCURRY_TICKS);
		} else if (animation.phase() == AnimalCrossingFishingPhase.CATCH_ANIMATING) {
			pulse *= 1.0F + (float) Math.sin(animation.catchAnimationTicks() * 0.8D) * 0.09F;
		}

		double dx = hook.getX() - display.getX();
        double dz = hook.getZ() - display.getZ();
        float yaw = (float) Math.atan2(dz, dx);
        Quaternionf rotation = new Quaternionf()
                .rotateY(-yaw)
                .rotateX((float) Math.toRadians(90.0D));
		float size = personality.size();
		Transformation transformation = new Transformation(
				new Vector3f(-0.5F, -0.5F, 0.0F),
                rotation,
                new Vector3f(1.22F * pulse * size, 0.72F * pulse * size, size),
				new Quaternionf()
		);
		((DisplayEntityAccessor) display).mainmod$setTransformation(transformation);
	}
}
