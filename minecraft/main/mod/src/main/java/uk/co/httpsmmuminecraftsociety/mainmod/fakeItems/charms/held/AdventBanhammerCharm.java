package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackBlockCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmStackData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.AttackEntityCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class AdventBanhammerCharm implements AttackBlockCallbackCharm, AttackEntityCallbackCharm,
        BaseItemChangeCallbackCharm {
    public static final int CHARM_ID = 63;
    private static final String USES_KEY = "mainmod_advent_banhammer_uses";
    private static final Set<ItemStack> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerPlayer, Long> BONKED_UNTIL = new WeakHashMap<>();

    public static boolean consumeBonked(ServerPlayer player) {
        Long until = BONKED_UNTIL.remove(player);
        return until != null && until > System.currentTimeMillis();
    }

    public static boolean isBanhammer(ItemStack stack) {
        return CharmStackData.getStoredCharms(stack).stream().anyMatch(charm -> charm.charmId() == CHARM_ID);
    }

    @Override
    public void enableEffectForItem(ItemStack stack, int charmLevel) {
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.MAX_DAMAGE, 3);
        stack.set(DataComponents.DAMAGE, uses(stack));
        stack.remove(DataComponents.REPAIRABLE);
    }

    @Override
    public void disableEffectForItem(ItemStack stack, int charmLevel) { }

    @Override
    public InteractionResult onAttackBlock(ItemStack stack, ServerPlayer player, ServerLevel level,
                                           InteractionHand hand, BlockPos pos, Direction direction, int charmLevel) {
        return hand == InteractionHand.MAIN_HAND && stack == player.getMainHandItem()
                ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public InteractionResult onAttackEntity(ItemStack stack, ServerPlayer player, ServerLevel level,
                                            InteractionHand hand, Entity entity, @Nullable EntityHitResult hit,
                                            int charmLevel) {
        if (hand != InteractionHand.MAIN_HAND || stack != player.getMainHandItem()) return InteractionResult.PASS;
        if (!(entity instanceof ServerPlayer victim) || victim == player || victim.hasDisconnected()
                || victim.getGameProfile().name().equalsIgnoreCase("MMU_Admin")
                || uses(stack) >= 3) return InteractionResult.FAIL;
        if (!PENDING.add(stack)) return InteractionResult.FAIL;

        GameplayGrpcService.useAdventBanhammer(victim.getUUID().toString(), victim.getGameProfile().name())
                .whenComplete((response, error) ->
                level.getServer().execute(() -> {
                    PENDING.remove(stack);
                    if (error != null || response == null || !response.getAccepted()) {
                        player.sendSystemMessage(Component.literal("The banhammer could not connect. Try again.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    BONKED_UNTIL.put(victim, System.currentTimeMillis() + 15_000);
                    int used = uses(stack) + 1;
                    if (used == 3) {
                        stack.shrink(1);
                    } else {
                        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        data.putInt(USES_KEY, used);
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                        stack.set(DataComponents.DAMAGE, used);
                    }
                }));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static int uses(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(USES_KEY, 0), 0, 3);
    }
}
