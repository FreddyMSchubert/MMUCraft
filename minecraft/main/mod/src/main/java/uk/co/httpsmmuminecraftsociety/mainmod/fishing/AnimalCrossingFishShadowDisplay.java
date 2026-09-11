package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.mojang.math.Transformation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.DisplayEntityAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.ItemDisplayEntityAccessor;

public final class AnimalCrossingFishShadowDisplay {
	public static final int ARRIVAL_TICKS = 18;
	public static final int SCURRY_TICKS = 16;
	private static final float ITEM_TEXTURE_PIXELS = 16.0F;
	private static final String SHADOW_MODEL_MARKER = "mainmod:fishing_shadow";

	private AnimalCrossingFishShadowDisplay() {}

	public record AnimationState(
			AnimalCrossingFishingPhase phase,
			double orbitDegrees,
			double fishDistance,
			float textureAngleDegrees,
			float textureLengthPixels,
			int arrivalTicks,
			int pauseTicks
	) {}

    public static Display.ItemDisplay create(
			ServerLevel level,
			ItemStack catchResult,
			boolean genericShadow
	) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setInvisible(false);

		ItemStack shadowStack;
		if (genericShadow) {
			FishShapes[] shapes = FishShapes.values();
			shadowStack = new ItemStack(Items.PAPER);
			shadowStack.set(DataComponents.ITEM_MODEL, Identifier.parse(
					shapes[level.getRandom().nextInt(shapes.length)].value()
			));
		} else {
			shadowStack = catchResult.copyWithCount(1);
			shadowStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
			if (FakeItems.getFakeItemFromStack(shadowStack) == null) {
				Identifier itemId = BuiltInRegistries.ITEM.getKey(shadowStack.getItem());
				shadowStack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(
						MainMod.MOD_ID,
						"fishing_shadow/" + itemId.getPath()
				));
			} else {
				CustomModelData modelData = shadowStack.getOrDefault(
						DataComponents.CUSTOM_MODEL_DATA,
						CustomModelData.EMPTY
				);
				shadowStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
						modelData.floats(),
						modelData.flags(),
						append(modelData.strings(), SHADOW_MODEL_MARKER),
						modelData.colors()
				));
			}
		}
        ((ItemDisplayEntityAccessor) display).mainmod$setItemStack(shadowStack);
        ((ItemDisplayEntityAccessor) display).mainmod$setItemTransform(ItemDisplayContext.FIXED);

        DisplayEntityAccessor accessor = (DisplayEntityAccessor) display;
        accessor.mainmod$setBillboardConstraints(Display.BillboardConstraints.FIXED);
        accessor.mainmod$setTransformationInterpolationDuration(0);
        accessor.mainmod$setTransformationInterpolationDelay(0);
        accessor.mainmod$setViewRange(32.0F);
		accessor.mainmod$setBrightnessOverride(new Brightness(0, 0));
        accessor.mainmod$setShadowRadius(0.0F);
        accessor.mainmod$setShadowStrength(0.0F);
        accessor.mainmod$setWidth(1.6F);
        accessor.mainmod$setHeight(1.6F);
        return display;
    }

	private static <T> java.util.List<T> append(java.util.List<T> values, T value) {
		java.util.ArrayList<T> copy = new java.util.ArrayList<>(values);
		copy.add(value);
		return java.util.List.copyOf(copy);
	}

	public static void position(
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

		float appearanceScale = 1.0F;
		if (animation.phase() == AnimalCrossingFishingPhase.ARRIVING) {
			appearanceScale = Math.max(0.08F, (ARRIVAL_TICKS - animation.arrivalTicks()) / (float) ARRIVAL_TICKS);
		} else if (animation.phase() == AnimalCrossingFishingPhase.SCURRYING) {
			appearanceScale = Math.max(0.08F, animation.pauseTicks() / (float) SCURRY_TICKS);
		}

		double dx = hook.getX() - display.getX();
        double dz = hook.getZ() - display.getZ();
		float yaw = (float) Math.atan2(dz, dx);
        Quaternionf rotation = new Quaternionf()
				.rotateY(-yaw + (float) Math.toRadians(90.0F - animation.textureAngleDegrees()))
                .rotateX((float) Math.toRadians(90.0D));
		float size = appearanceScale * personality.size()
				* ITEM_TEXTURE_PIXELS / animation.textureLengthPixels();
		Transformation transformation = new Transformation(
				new Vector3f(-0.5F, -0.5F, 0.0F),
                rotation,
				new Vector3f(size, size, size),
				new Quaternionf()
		);
		((DisplayEntityAccessor) display).mainmod$setTransformation(transformation);
	}
}
