package uk.co.httpsmmuminecraftsociety.mainmod.snowball;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.ArrayList;
import java.util.List;

public final class SnowballMachineGun {
    private static final int CAPACITY = 64;
    private static final int LOAD_SIZE = 16;
    private static final String AMMO_KEY = "mainmod_snowball_ammo";

    private SnowballMachineGun() {}

    public static boolean isGun(ItemStack stack) {
        return FakeItems.isSpecificFakeItem(stack, "snowball-machine-gun");
    }

    private static int ammo(ItemStack stack) {
        int stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(AMMO_KEY, 0);
        return Math.max(0, Math.min(CAPACITY, stored));
    }

    private static void setAmmo(ItemStack stack, int amount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(AMMO_KEY, amount));
        CustomModelData model = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(model.floats(), List.of(amount > 0), model.strings(), model.colors()));
        var lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        Component count = Component.literal("Snowballs: " + amount + "/" + CAPACITY)
                .withStyle(style -> style.withItalic(false));
        if (lines.isEmpty()) lines.add(count);
        else lines.set(0, count);
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    public static InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || ammo(gun) == 0) {
            if (!level.isClientSide()) reload(gun, player);
        } else {
            player.startUsingItem(hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static void reload(ItemStack gun, Player player) {
        int current = ammo(gun);
        if (current > CAPACITY - LOAD_SIZE) return;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(Items.SNOWBALL) && stack.getCount() == LOAD_SIZE) {
                stack.shrink(LOAD_SIZE);
                setAmmo(gun, current + LOAD_SIZE);
                player.getInventory().setChanged();
                player.sendOverlayMessage(Component.literal("Snowballs: " + (current + LOAD_SIZE) + "/" + CAPACITY));
                return;
            }
        }
        player.sendOverlayMessage(Component.literal("Need a full stack of 16 snowballs"));
    }

    public static void tick(Level level, LivingEntity user, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int current = ammo(stack);
        if (current <= 0) {
            user.stopUsingItem();
            return;
        }
        Projectile.spawnProjectileFromRotation(Snowball::new, serverLevel, new ItemStack(Items.SNOWBALL),
                user, 0.0F, 1.5F, 1.0F);
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.2F, 1.0F);
        setAmmo(stack, current - 1);
        if (current == 1) user.stopUsingItem();
    }
}
