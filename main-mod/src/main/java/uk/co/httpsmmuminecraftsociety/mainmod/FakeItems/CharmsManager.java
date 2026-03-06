package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CharmFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.UseCallbackCharm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CharmsManager
{
    public static final String CHARM_ABILITES_COMPOUND_ID = "charm_abilities";

    public static List<CharmFakeItem> getAbilitiesFromItemStack(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return List.of();
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Optional<int[]> abilities = nbt.getIntArray(CHARM_ABILITES_COMPOUND_ID);
        if (abilities.isEmpty()) return List.of();
        List<CharmFakeItem> charms = new ArrayList<>();
        for (int ability : abilities.get())
            charms.add(FakeItems.CHARM_EFFECT_ID_MAP.get(ability));
        return charms;
    }

    private static ItemStack triggerEquippedTickCallbacks(ItemStack stack, ServerPlayer player, ServerLevel level) {
        List<CharmFakeItem> charmFakeItems = getAbilitiesFromItemStack(stack);
        for (CharmFakeItem cfi : charmFakeItems) {
            if (!(cfi.getCharm() instanceof EquippedTickCallbackCharm equippedCharm)) continue;
            stack = equippedCharm.equippedTick(stack, player, level);
        }
        return stack;
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // pretick even if unequipped
            for (Map.Entry<Integer, CharmFakeItem> def : FakeItems.CHARM_EFFECT_ID_MAP.entrySet()) {
                if (!(def.getValue().getCharm() instanceof TickCallbackCharm tickCharm)) continue;
                tickCharm.onTick(player, server);
            }

            // tick actually present stuff
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack current = player.getItemBySlot(slot);
                if (current.isEmpty()) continue;

                ItemStack updated = triggerEquippedTickCallbacks(current, player, server);
                if (updated == null) updated = ItemStack.EMPTY;

                if (updated != current && !ItemStack.isSameItemSameComponents(current, updated)) {
                    player.setItemSlot(slot, updated);
                }
            }
        }
    }

    public static InteractionResult onItemUse(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        List<CharmFakeItem> charmFakeItems = getAbilitiesFromItemStack(stack);
        boolean hasSucceeded = false;
        for (CharmFakeItem cfi : charmFakeItems) {
            if (!(cfi.getCharm() instanceof UseCallbackCharm useCharm)) continue;
            stack = useCharm.onUse(stack, (ServerPlayer) player, (ServerLevel) level);
            player.setItemInHand(interactionHand, stack);
            hasSucceeded = true;
        }
        if (hasSucceeded) return InteractionResult.SUCCESS;
        else return InteractionResult.FAIL;
    }
}
