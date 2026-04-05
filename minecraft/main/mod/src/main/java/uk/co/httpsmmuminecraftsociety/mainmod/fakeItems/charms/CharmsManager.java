package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfDisplacementCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfInsomniaCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfReturningCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.*;

public class CharmsManager
{
    private static final Map<Integer, Charm> CHARMS_REGISTRY = Map.ofEntries(
            Map.entry(1, new CraftingStaffCharm()),
            Map.entry(2, new EnderChestStaffCharm()),
            Map.entry(3, new HeartCharm()),
            Map.entry(7, new RunningShoesCharm()),
            Map.entry(8, new CandleOfTheDeepCharm()),
            Map.entry(9, new HikingBootsCharm()),
            Map.entry(12, new GiantsBootsCharm()),
            Map.entry(13, new LeprechaunBootsCharm()),
            Map.entry(16, new ExtendoGripCharm()),
            Map.entry(17, new BunnyPajamasCharm()),
            Map.entry(19, new SpiderPajamasCharm()),
            Map.entry(20, new CaveSpiderPajamasCharm()),
            Map.entry(21, new GoopHandCharm()),
            Map.entry(22, new WingedShoesCharm()),
            Map.entry(25, new PotionOfReturningCharm()),
            Map.entry(26, new UmbrellaCharm()),
            Map.entry(27, new PotionOfDisplacementCharm()),
            Map.entry(28, new SculkPhialCharm()),
            Map.entry(29, new PotionOfInsomniaCharm()),
            Map.entry(30, new WalletCharm()),
            Map.entry(31, new VeinminerCharm())
    );
    public static Charm charmFromId(int charmId) {
        return CHARMS_REGISTRY.get(charmId);
    }

    public record CharmInstance(
            FakeItem fakeItem,
            CharmItemFeature feature,
            int level
    ) {
        public boolean isBroken() {
            return level <= 0;
        }

        public Charm charm() {
            return feature.charm();
        }

        public int charmId() {
            return feature.charmId();
        }
    }

    // Some generic utils

    public static List<CharmInstance> getCharmInstances(ItemStack stack) {
        return CharmStackData.getStoredCharms(stack).stream()
                .map(CharmsManager::resolveCharmInstance)
                .filter(Objects::nonNull)
                .toList();
    }
    private static CharmInstance resolveCharmInstance(StoredCharmData storedCharm) {
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
        if (fakeItem == null) {
            return null;
        }

        CharmItemFeature feature = fakeItem.getFeature(CharmItemFeature.class);
        if (feature == null) {
            return null;
        }

        return new CharmInstance(fakeItem, feature, storedCharm.level());
    }
    public static boolean hasAbility(ItemStack stack, Class<? extends Charm> charmClass) {
        for (CharmInstance ability : getCharmInstances(stack)) {
            if (ability.feature().charm().getClass() == charmClass) return true;
        }
        return false;
    }

    private static void tickActiveUseCharms(ServerPlayer player, ServerLevel level) {
        if (!player.isUsingItem()) return;

        ItemStack activeStack = player.getActiveItem();
        if (activeStack.isEmpty()) return;

        CharmInstance activeCharm = null;
        for (CharmInstance instance : getCharmInstances(activeStack)) {
            if (instance.isBroken()) continue;
            if (instance.charm() instanceof ConsumableCallbacksCharm) {
                activeCharm = instance;
                break;
            }
        }
        if (activeCharm == null) return;

        Consumable consumable = activeStack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;

        ConsumableCallbacksCharm callbacksCharm = (ConsumableCallbacksCharm) activeCharm.charm();
        int elapsedTicks = consumable.consumeTicks() - player.getUseItemRemainingTicks();

        if (player.getUseItemRemainingTicks() <= 1) {
            callbacksCharm.onConsumeFinished(
                    activeStack,
                    player,
                    level,
                    elapsedTicks,
                    activeCharm.level()
            );

            player.stopUsingItem();
            return;
        }

        callbacksCharm.onConsumeTick(activeStack, player, level, elapsedTicks, activeCharm.level());
    }
    private static void triggerEquippedTickCallbacks(ItemStack stack, ServerPlayer player, ServerLevel level, EquipmentSlot slot) {
        for (CharmInstance instance : getCharmInstances(stack)) {
            if (instance.isBroken()) continue;
            EquippableCharmItemFeature equippable = instance.fakeItem().getFeature(EquippableCharmItemFeature.class);
            if (equippable != null && equippable.equippable().slot() != slot) continue;
            if (instance.charm() instanceof EquippedTickCallbackCharm equippedCharm) {
                equippedCharm.equippedTick(stack, player, level, instance.level());
            }
        }
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // uniquipped tick
            for (Map.Entry<Integer, FakeItem> entry : FakeItems.CHARM_ID_MAP.entrySet()) {
                CharmItemFeature feature = entry.getValue().getFeature(CharmItemFeature.class);
                if (feature == null || !(feature.charm() instanceof TickCallbackCharm tickCallbackCharm)) {
                    continue;
                }
                tickCallbackCharm.onTick(player, server);
            }

            // equipped tick
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack current = player.getItemBySlot(slot);
                if (current.isEmpty()) {
                    continue;
                }

                triggerEquippedTickCallbacks(current, player, server, slot);
            }

            // actively used tick
            tickActiveUseCharms(player, server);
        }
    }

    public static InteractionResult onItemUse(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        List<CharmInstance> instances = getCharmInstances(stack);
        if (instances.isEmpty()) return null;

        for (CharmInstance instance : instances) {
            if (instance.isBroken()) continue;
            if (!(instance.charm() instanceof UseCallbackCharm useCallbackCharm)) continue;

            return useCallbackCharm.onUse(
                    stack,
                    (ServerPlayer) player,
                    (ServerLevel) level,
                    instance.level()
            );
        }
        return null;
    }

    public static void onAfterBlockBreak(Level level, Player player, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity)
    {
        if (!(player instanceof ServerPlayer)) return;
        if (!(level instanceof ServerLevel)) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            for (CharmInstance instance : getCharmInstances(stack))
            {
                if (instance.isBroken()) continue;
                EquippableCharmItemFeature equippable = instance.fakeItem().getFeature(EquippableCharmItemFeature.class);
                if (equippable != null && equippable.equippable().slot() != slot) continue;
                if (instance.charm() instanceof AfterBlockBreakCallbackCharm afterBreakCharm) {
                    afterBreakCharm.afterBlockBreak(stack, (ServerPlayer) player, (ServerLevel) level, blockPos, blockState, instance.level());
                }
            }
        }
    }
}
