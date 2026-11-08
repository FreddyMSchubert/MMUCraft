package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.util.Prediction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FishItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.DisplayEntityAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.ItemDisplayEntityAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FishingPlaques {
    private static final String FISH_TAG = "mainmod_plaque_fish";
    private static final String DISPLAY_TAG = "mainmod_plaque_display";
    public static final String FRAME_TAG = "mainmod_fishing_plaque";
    public static final String ACTION_BAR_MARKER = "mainmod:fishing_plaque";
    private static final Map<UUID, Map<UUID, ClickState>> CLICKS = new HashMap<>();

    private FishingPlaques() {}

    public static boolean isPlaque(ItemStack stack) {
        FakeItem item = FakeItems.getFakeItemFromStack(stack);
        return item != null && item.id().startsWith("deco-fishing-plaque-");
    }

    public static boolean isFish(ItemStack stack) {
        FakeItem item = FakeItems.getFakeItemFromStack(stack);
        return (item != null && item.getFeature(FishItemFeature.class) != null)
                || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .contains("mainmod_fish_length_cm");
    }

    public static ItemStack fish(ItemStack plaque) {
        CompoundTag tag = plaque.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.read(FISH_TAG, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    public static ItemStack withFish(ItemStack plaque, ItemStack fish) {
        ItemStack result = plaque.copyWithCount(1);
        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(FISH_TAG);
        if (isFish(fish)) tag.store(FISH_TAG, ItemStack.CODEC, fish.copyWithCount(1));
        if (tag.isEmpty()) result.remove(DataComponents.CUSTOM_DATA);
        else result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        FakeItem fishItem = FakeItems.getFakeItemFromStack(fish);
        result.set(DataComponents.CUSTOM_NAME, fish.isEmpty()
                ? FakeItems.getFakeItemFromStack(plaque).createItemStack().getHoverName()
                : Component.literal((fishItem == null ? fish.getHoverName().getString() : fishItem.title())
                + " Fishing Plaque"));
        return result;
    }

    public static void placed(ItemFrame frame) {
        if (!isPlaque(frame.getItem())) return;
        frame.addTag(FRAME_TAG);
        ItemStack fish = fish(frame.getItem());
        if (isFish(fish) && frame.level() instanceof ServerLevel level) addDisplays(level, frame, fish);
    }

    public static void removed(ItemFrame frame) {
        for (Entity passenger : List.copyOf(frame.getPassengers())) {
            if (passenger.entityTags().contains(DISPLAY_TAG)) passenger.discard();
        }
        CLICKS.values().forEach(states -> states.remove(frame.getUUID()));
    }

    public static InteractionResult interact(ItemFrame frame, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (!isPlaque(frame.getItem())) return null;
        if (!(player instanceof ServerPlayer serverPlayer) || !(frame.level() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        ItemStack previous = fish(frame.getItem());
        if (isFish(held) || (player.isShiftKeyDown() && held.isEmpty() && !previous.isEmpty())) {
            ItemStack next = isFish(held) ? held.copyWithCount(1) : ItemStack.EMPTY;
            for (Entity passenger : List.copyOf(frame.getPassengers())) {
                if (passenger.entityTags().contains(DISPLAY_TAG)) passenger.discard();
            }
            frame.setItem(DecoBlocksManager.hideCustomNameForFrame(withFish(frame.getItem(), next)), false);
            if (!next.isEmpty()) addDisplays(level, frame, next);
            if (!player.isCreative() && !next.isEmpty()) held.shrink(1);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false, Prediction.SERVER_ONLY);
            CLICKS.values().forEach(states -> states.remove(frame.getUUID()));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (previous.isEmpty()) return InteractionResult.SUCCESS_SERVER;

        long now = System.currentTimeMillis();
        CLICKS.values().removeIf(states -> {
            states.values().removeIf(state -> now - state.time() > 60_000);
            return states.isEmpty();
        });
        Map<UUID, ClickState> states = CLICKS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        ClickState prior = states.get(frame.getUUID());
        int index = prior == null ? 0 : (prior.index() + 1) % 3;
        states.put(frame.getUUID(), new ClickState(index, now));
        FakeItem item = FakeItems.getFakeItemFromStack(previous);
        FishRarity rarity = FishingCatches.fishRarity(previous).orElse(item == null
                ? FishRarity.COMMON : item.getFeature(FishItemFeature.class).personality().rarity());
        Component message = switch (index) {
            case 0 -> Component.literal((item == null ? previous.getHoverName().getString() : item.title())
                    + " · " + rarity.displayName()).withColor(rarity.colorRgb());
            case 1 -> catcher(previous, level).copy()
                    .append(Component.literal(" · " + size(previous)).withColor(rarity.colorRgb()));
            default -> item == null || item.tooltip().isEmpty()
                    ? previous.getHoverName().copy().withColor(rarity.colorRgb()).withStyle(ChatFormatting.ITALIC)
                    : item.tooltip().getFirst().copy().withColor(rarity.colorRgb()).withStyle(ChatFormatting.ITALIC);
        };
        serverPlayer.sendSystemMessage(message.copy().withStyle(style -> style.withInsertion(ACTION_BAR_MARKER)), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Component catcher(ItemStack fish, ServerLevel level) {
        for (Component line : fish.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            if (!line.getString().startsWith("Caught by: ")) continue;
            String name = line.getString().substring("Caught by: ".length());
            CompoundTag tag = fish.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            String catcherId = tag.getStringOr(FishingCatches.CATCHER_UUID_TAG, "");
            Integer color = null;
            try {
                if (!catcherId.isEmpty()) color = PlayerStatsSync.colorFor(level.getServer(), UUID.fromString(catcherId));
            } catch (IllegalArgumentException ignored) {
                // Try the saved username when the stored UUID is invalid.
            }
            if (color == null) color = catcherColorByName(level.getServer(), name);
            Component coloredName = Component.literal(name).withColor(color == null ? 0xAAAAAA : color);
            PlayerTeam team = level.getServer().getScoreboard().getPlayersTeam(name);
            return Component.literal("Caught by: ").withStyle(ChatFormatting.GRAY)
                    .append(team == null ? coloredName : PlayerTeam.formatNameForTeam(team, coloredName));
        }
        return Component.literal("Catcher unknown").withStyle(ChatFormatting.GRAY);
    }

    private static Integer catcherColorByName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return PlayerStatsSync.colorFor(online);

        PlayerTeam team = server.getScoreboard().getPlayersTeam(name);
        if (team != null && team.getName().startsWith("mmu")) {
            var savedColor = team.getDisplayName().getStyle().getColor();
            if (savedColor != null) return savedColor.getValue();
        }

        return null;
    }

    private static String size(ItemStack fish) {
        CompoundTag tag = fish.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        double length = tag.getDoubleOr("mainmod_fish_length_cm", 0.0);
        return length > 0 ? String.format(Locale.ROOT, "%.1f cm", length) : "Size unknown";
    }

    private static void addDisplays(ServerLevel level, ItemFrame frame, ItemStack fish) {
        FakeItem item = FakeItems.getFakeItemFromStack(fish);
        FishItemFeature feature = item == null ? null : item.getFeature(FishItemFeature.class);
        FishRarity rarity = FishingCatches.fishRarity(fish).orElse(feature == null
                ? FishRarity.COMMON : feature.personality().rarity());
        ItemStack sign = FakeItems.createFakeItemStack("fishing-plaque-sign-" + rarity.name().toLowerCase(Locale.ROOT), 1);
        float plaqueOrigin = -1.0F / 32.0F;
        addDisplay(level, frame, sign, 0.5F, plaqueOrigin, 0.0F, ItemDisplayContext.FIXED);
        CompoundTag tag = fish.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        double length = tag.getDoubleOr("mainmod_fish_length_cm", feature == null ? 50.0 : feature.length().averageCm());
        float scale = FishSize.blocks(length) * 16.0F
                / (feature == null ? 16.0F : feature.personality().textureLengthPixels());
        float rightAngle = feature == null ? 90.0F : feature.plaqueRightAngle();
        boolean needsMirror = !plaqueFacesLeft(rightAngle);
        boolean hasMirroredModel = feature != null || fish.is(Items.COD) || fish.is(Items.SALMON)
                || fish.is(Items.TROPICAL_FISH) || fish.is(Items.PUFFERFISH);
        ItemStack displayFish = fish.copyWithCount(1);
        if (needsMirror && hasMirroredModel) {
            String id = feature == null ? BuiltInRegistries.ITEM.getKey(fish.getItem()).getPath() : item.id();
            displayFish.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(
                    MainMod.MOD_ID, "fishing_plaque/mirrored/" + id));
        }
        // The wood is one pixel thick, so place the fish center on its front face.
        // shortcut: Other tagged items keep their source direction until they have a mirrored model.
        addDisplay(level, frame, displayFish, scale, plaqueOrigin + 1.0F / 16.0F,
                needsMirror && hasMirroredModel ? plaqueRotation(rightAngle) : -plaqueRotation(rightAngle),
                ItemDisplayContext.NONE);
    }

    static boolean plaqueFacesLeft(float rightAngle) {
        return rightAngle > 180.0F;
    }

    static float plaqueRotation(float rightAngle) {
        return rightAngle - (plaqueFacesLeft(rightAngle) ? 270.0F : 90.0F);
    }

    private static void addDisplay(ServerLevel level, ItemFrame frame, ItemStack stack, float scale, float depth,
                                   float rotationDegrees, ItemDisplayContext context) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setPermanentlyInvulnerable(true);
        display.addTag(DISPLAY_TAG);
        display.setPos(frame.getX(), frame.getY(), frame.getZ());
        display.setYRot(frame.getDirection().toYRot());
        ((ItemDisplayEntityAccessor) display).mainmod$setItemStack(stack);
        ((ItemDisplayEntityAccessor) display).mainmod$setItemTransform(context);
        if (!level.addFreshEntity(display)) return;
        if (!display.startRiding(frame, true, false)) {
            display.discard();
            return;
        }
        frame.positionRider(display);
        ((DisplayEntityAccessor) display).mainmod$setTransformation(plaqueTransform(
                (float) (frame.getY() - display.getY()), depth, scale, rotationDegrees, false));
    }

    static Transformation plaqueTransform(float yOffset, float depth, float scale, float rotationDegrees, boolean mirrorX) {
        return new Transformation(new Vector3f(0.0F, yOffset, depth), new Quaternionf(),
                new Vector3f(mirrorX ? -scale : scale, scale, scale),
                new Quaternionf().rotateZ((float) Math.toRadians(rotationDegrees)));
    }

    private record ClickState(int index, long time) {}
}
