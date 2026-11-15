package uk.co.httpsmmuminecraftsociety.mainmod.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MarshmallowRoasting {
    private static final Map<UUID, Warming> WARMING = new HashMap<>();
    private static final int SAMPLE_TICKS = 5;
    private static final int ROAST_TICKS = 200;

    private MarshmallowRoasting() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % SAMPLE_TICKS != 0) return;
        UUID playerId = player.getUUID();
        InteractionHand hand = heldMarshmallows(player);
        if (hand == null) {
            WARMING.remove(playerId);
            return;
        }
        HitResult hit = player.pick(3.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            WARMING.remove(playerId);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        var state = player.level().getBlockState(pos);
        if (!state.is(BlockTags.CAMPFIRES) || !state.getValue(CampfireBlock.LIT)
                || player.position().distanceToSqr(Vec3.atCenterOf(pos)) >= 2.25D) {
            WARMING.remove(playerId);
            return;
        }
        Warming warming = WARMING.get(playerId);
        if (warming == null || warming.hand() != hand || !warming.pos().equals(pos)) {
            WARMING.put(playerId, new Warming(pos, hand, player.tickCount));
            return;
        }
        if (player.tickCount - warming.startedAtTick() < ROAST_TICKS) return;
        player.setItemInHand(hand, FakeItems.createFakeItemStack("food-marshmallows-roasted", 1));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your marshmallows are roasted."));
        WARMING.remove(playerId);
    }

    public static void disconnected(ServerPlayer player) {
        WARMING.remove(player.getUUID());
    }

    private static InteractionHand heldMarshmallows(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (FakeItems.isSpecificFakeItem(main, "food-marshmallows")) return InteractionHand.MAIN_HAND;
        ItemStack off = player.getOffhandItem();
        if (FakeItems.isSpecificFakeItem(off, "food-marshmallows")) return InteractionHand.OFF_HAND;
        return null;
    }

    private record Warming(BlockPos pos, InteractionHand hand, int startedAtTick) {}
}
