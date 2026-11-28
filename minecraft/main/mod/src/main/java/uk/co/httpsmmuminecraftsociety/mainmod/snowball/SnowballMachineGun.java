package uk.co.httpsmmuminecraftsociety.mainmod.snowball;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.ArrayList;
import java.util.List;

public final class SnowballMachineGun {
    private static final int CAPACITY = 128;
    private static final int SHOTS_PER_SNOWBALL = 2;
    private static final String SHOTS_KEY = "mainmod_snowball_shots";

    private SnowballMachineGun() {}

    public static void configure(ItemStack stack) {
        stack.set(DataComponents.CONSUMABLE, Consumable.builder()
                .consumeSeconds(3600.0F)
                .animation(ItemUseAnimation.CROSSBOW)
                .hasConsumeParticles(false)
                .build());
    }

    public static boolean isGun(ItemStack stack) {
        return stack.is(Items.REPEATING_COMMAND_BLOCK) && FakeItems.isSpecificFakeItem(stack, "snowball-machine-gun");
    }

    private static int shots(ItemStack stack) {
        int stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(SHOTS_KEY, 0);
        return Math.max(0, Math.min(CAPACITY * SHOTS_PER_SNOWBALL, stored));
    }

    private static int ammo(ItemStack stack) {
        return (shots(stack) + SHOTS_PER_SNOWBALL - 1) / SHOTS_PER_SNOWBALL;
    }

    private static void setShots(ItemStack stack, int amount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SHOTS_KEY, amount));
        setModelState(stack, amount > 0, isLoading(stack), isReloaded(stack));
        var lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        Component count = Component.literal("Snowballs: " + ammo(stack) + "/" + CAPACITY)
                .withStyle(style -> style.withItalic(false));
        if (lines.isEmpty()) lines.add(count);
        else lines.set(0, count);
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static boolean isLoading(ItemStack stack) {
        return Boolean.TRUE.equals(stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
                .getBoolean(1));
    }

    private static boolean isReloaded(ItemStack stack) {
        return Boolean.TRUE.equals(stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
                .getBoolean(2));
    }

    private static void setModelState(ItemStack stack, boolean loaded, boolean loading, boolean reloaded) {
        CustomModelData model = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(model.floats(), List.of(loaded, loading, reloaded), model.strings(), model.colors()));
    }

    public static InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        boolean reload = hand == InteractionHand.MAIN_HAND
                && player.getOffhandItem().is(Items.SNOWBALL)
                && ammo(gun) < CAPACITY;
        if (shots(gun) == 0 && !reload) return InteractionResult.CONSUME;
        setModelState(gun, shots(gun) > 0, reload, false);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    private static void reload(ItemStack gun, Player player) {
        int amount = loadFromOffhand(gun, player.getOffhandItem());
        if (amount == 0) return;
        player.getInventory().setChanged();
        player.sendOverlayMessage(Component.literal("Snowballs: " + ammo(gun) + "/" + CAPACITY));
    }

    static int loadFromOffhand(ItemStack gun, ItemStack offhand) {
        int current = shots(gun);
        if (!offhand.is(Items.SNOWBALL)) return 0;
        int amount = Math.min(offhand.getCount(), (CAPACITY * SHOTS_PER_SNOWBALL - current) / SHOTS_PER_SNOWBALL);
        if (amount == 0) return 0;
        offhand.shrink(amount);
        setShots(gun, current + amount * SHOTS_PER_SNOWBALL);
        return amount;
    }

    static void spendShot(ItemStack gun) {
        if (shots(gun) > 0) setShots(gun, shots(gun) - 1);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isUsingItem() && isGun(player.getUseItem())) tick(player);
        }
    }

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        ItemStack stack = player.getUseItem();
        int ticksRemaining = player.getUseItemRemainingTicks();
        if (isLoading(stack)) {
            int elapsed = stack.getUseDuration(player) - ticksRemaining;
            int charge = CrossbowItem.getChargeDuration(stack, player);
            if (elapsed == Math.max(1, charge / 5))
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (elapsed == Math.max(2, charge / 2))
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (elapsed >= charge && !isReloaded(stack)) {
                reload(stack, player);
                setModelState(stack, shots(stack) > 0, true, true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }
        int current = shots(stack);
        if (current <= 0) return;
        Projectile.spawnProjectileFromRotation(MachineGunSnowball::new, level, new ItemStack(Items.SNOWBALL),
                player, 0.0F, 2.0F, 1.25F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.2F, 1.0F);
        spendShot(stack);
        player.sendOverlayMessage(Component.literal("Snowballs: " + ammo(stack) + "/" + CAPACITY));
    }

    public static void release(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
        if (isLoading(stack)) {
            if (level instanceof ServerLevel && user instanceof Player player && !isReloaded(stack)
                    && stack.getUseDuration(user) - ticksRemaining >= CrossbowItem.getChargeDuration(stack, user)) {
                reload(stack, player);
            }
            setModelState(stack, shots(stack) > 0, false, false);
        }
    }
}
