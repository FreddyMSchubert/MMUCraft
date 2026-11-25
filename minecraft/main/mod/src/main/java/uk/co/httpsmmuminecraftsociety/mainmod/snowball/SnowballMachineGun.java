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
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.ArrayList;
import java.util.List;

public final class SnowballMachineGun {
    private static final int CAPACITY = 128;
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
        setModelState(stack, amount > 0, isLoading(stack));
        var lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        Component count = Component.literal("Snowballs: " + amount + "/" + CAPACITY)
                .withStyle(style -> style.withItalic(false));
        if (lines.isEmpty()) lines.add(count);
        else lines.set(0, count);
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static boolean isLoading(ItemStack stack) {
        return Boolean.TRUE.equals(stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
                .getBoolean(1));
    }

    private static void setModelState(ItemStack stack, boolean loaded, boolean loading) {
        CustomModelData model = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(model.floats(), List.of(loaded, loading), model.strings(), model.colors()));
    }

    public static InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        boolean reload = hand == InteractionHand.MAIN_HAND
                && player.getOffhandItem().is(Items.SNOWBALL)
                && ammo(gun) < CAPACITY;
        if (ammo(gun) == 0 && !reload) return InteractionResult.SUCCESS;
        setModelState(gun, ammo(gun) > 0, reload);
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    private static void reload(ItemStack gun, Player player) {
        int amount = loadFromOffhand(gun, player.getOffhandItem());
        if (amount == 0) return;
        player.getInventory().setChanged();
        player.sendOverlayMessage(Component.literal("Snowballs: " + ammo(gun) + "/" + CAPACITY));
    }

    static int loadFromOffhand(ItemStack gun, ItemStack offhand) {
        int current = ammo(gun);
        if (!offhand.is(Items.SNOWBALL) || current == CAPACITY) return 0;
        int amount = Math.min(offhand.getCount(), CAPACITY - current);
        offhand.shrink(amount);
        setAmmo(gun, current + amount);
        return amount;
    }

    public static void tick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (isLoading(stack)) {
            int elapsed = stack.getUseDuration(user) - ticksRemaining;
            int charge = CrossbowItem.getChargeDuration(stack, user);
            if (elapsed == Math.max(1, charge / 5))
                level.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (elapsed == Math.max(2, charge / 2))
                level.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (elapsed == charge) {
                if (user instanceof Player player) reload(stack, player);
                level.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }
        int current = ammo(stack);
        if (current <= 0) return;
        Projectile.spawnProjectileFromRotation(Snowball::new, serverLevel, new ItemStack(Items.SNOWBALL),
                user, 0.0F, 1.5F, 1.0F);
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.2F, 1.0F);
        setAmmo(stack, current - 1);
    }

    public static void release(ItemStack stack) {
        if (isLoading(stack)) setModelState(stack, ammo(stack) > 0, false);
    }
}
