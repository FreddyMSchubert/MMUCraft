package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyAdvancementPolicy;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.money.AdvancementMoney;
import uk.co.httpsmmuminecraftsociety.mainmod.money.MoneyHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
final class GameplayDailyOperations {
    private GameplayDailyOperations() {}

    static GenerateDailyTasksResponse generateDailyTasksOnMainThread(GenerateDailyTasksRequest request) {
        List<String> tasks = DailyTaskManager.generate(
                request.getUserId(),
                request.getMinecraftUsername(),
                request.getPeriodKey(),
                request.getCount(),
                request.getUnixMs(),
                request.getExcludedTaskIdsList()
        );
        return GenerateDailyTasksResponse.newBuilder()
                .setGenerated(true)
                .addAllTaskJson(tasks)
                .setMessage("Daily tasks generated.")
                .build();
    }

    static ClaimDailyTaskResponse claimDailyTaskOnMainThread(ClaimDailyTaskRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(request.getMinecraftUsername());
        if (player == null || player.hasDisconnected()) {
            return ClaimDailyTaskResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to claim this daily.")
                    .build();
        }

        DailyTaskDefinition.ClaimResult result = DailyTaskManager.claim(
                player,
                request.getUserId(),
                request.getPeriodKey(),
                request.getTaskJson()
        );
        if (result.claimed()) MoneyHelper.SendBalanceMessage(player, result.message());
        return ClaimDailyTaskResponse.newBuilder()
                .setClaimed(result.claimed())
                .setOnline(true)
                .setTaskJson(request.getTaskJson())
                .setMessage(result.message())
                .build();
    }

    static PickDailyAdvancementResponse pickDailyAdvancementOnMainThread(PickDailyAdvancementRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return PickDailyAdvancementResponse.newBuilder()
                    .setSelected(false)
                    .setOnline(false)
                    .setMessage("You have to be online on the server to receive a daily advancement target.")
                    .build();
        }

        PlayerAdvancements playerAdvancements = player.getAdvancements();
        Set<AdvancementHolder> visible = playerAdvancements.visible;
        List<AdvancementHolder> candidates = new ArrayList<>();
        AdvancementTree tree = playerAdvancements.tree;

        for (AdvancementHolder holder : visible) {
            AdvancementNode node = tree.get(holder);
            AdvancementNode parent = node == null ? null : node.parent();
            int reward = AdvancementMoney.moneyForAdvancement(holder.id(), holder.value().rewards().experience());
            if (holder.value().display().isEmpty()
                    || node == null
                    || parent == null
                    || !DailyAdvancementPolicy.allows(holder.id(), node.root().holder().id())
                    || !playerAdvancements.getOrStartProgress(parent.holder()).isDone()
                    || reward < 1
                    || reward > MAX_DAILY_ADVANCEMENT_REWARD
                    || holder.id().toString().equals(request.getExcludedAdvancementId())) {
                continue;
            }

            AdvancementProgress progress = playerAdvancements.getOrStartProgress(holder);
            if (!progress.isDone()) {
                candidates.add(holder);
            }
        }

        if (candidates.isEmpty()) {
            return PickDailyAdvancementResponse.newBuilder()
                    .setSelected(false)
                    .setOnline(true)
                    .setMessage("No suitable next-step advancements are available right now.")
                    .build();
        }

        candidates.sort(Comparator.comparing(holder -> holder.id().toString()));
        String seed = request.getPeriodKey() + ":" + normalize(username) + ":" + request.getUnixMs();
        AdvancementHolder selected = candidates.get(Math.floorMod(seed.hashCode(), candidates.size()));
        DisplayInfo display = selected.value().display().orElseThrow();
        AdvancementNode node = tree.get(selected);
        AdvancementNode root = node == null ? null : node.root();

        String tabTitle = root == null
                ? selected.id().getNamespace()
                : root.holder().value().display()
                        .map(rootDisplay -> rootDisplay.getTitle().getString())
                        .orElse(root.holder().id().toString());

        int baseReward = AdvancementMoney.moneyForAdvancement(selected.id(), selected.value().rewards().experience());
        int bonusReward = Math.max(5, Math.min(42, baseReward));

        return PickDailyAdvancementResponse.newBuilder()
                .setSelected(true)
                .setOnline(true)
                .setAdvancementId(selected.id().toString())
                .setTitle(display.getTitle().getString())
                .setTabTitle(tabTitle)
				.setIconItem(MinecraftItemIdentifier.forStack(display.getIcon().create()))
                .setBaseRewardDabloons(baseReward)
                .setBonusRewardDabloons(bonusReward)
                .setMessage("Daily advancement target selected.")
                .build();
    }

    static ClaimDailyAdvancementResponse claimDailyAdvancementOnMainThread(ClaimDailyAdvancementRequest request) {
        MinecraftServer server = GrpcBridge.minecraftServer();
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }

        String username = request.getMinecraftUsername();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player == null || player.hasDisconnected()) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(false)
                    .setCompleted(false)
                    .setMessage("You have to be online on the server to claim the daily advancement bonus.")
                    .build();
        }

        AdvancementHolder holder = server.getAdvancements().get(Identifier.parse(request.getAdvancementId()));
        if (holder == null) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(false)
                    .setMessage("That daily advancement is no longer available on the server.")
                    .build();
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            String title = holder.value().display()
                    .map(display -> display.getTitle().getString())
                    .orElse(request.getAdvancementId());
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(false)
                    .setMessage("Complete " + title + " in-game first, then claim this daily.")
                    .build();
        }

        if (request.getCheckOnly()) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(true)
                    .setMessage("Daily advancement completed.")
                    .build();
        }

        int reward = Math.max(0, request.getBonusRewardDabloons());
        if (!MoneyHelper.GainMoney(player, reward)) {
            return ClaimDailyAdvancementResponse.newBuilder()
                    .setClaimed(false)
                    .setOnline(true)
                    .setCompleted(true)
                    .setMessage("Could not grant the daily advancement bonus.")
                    .build();
        }

        MoneyHelper.SendBalanceMessage(player,
                "You received " + reward + " bonus dabloons for completing " + request.getAdvancementId() + ".");
        return ClaimDailyAdvancementResponse.newBuilder()
                .setClaimed(true)
                .setOnline(true)
                .setCompleted(true)
                .setMessage("You received " + reward + " bonus dabloons for completing " + request.getAdvancementId() + ".")
                .build();
    }
    private static final int MAX_DAILY_ADVANCEMENT_REWARD = 20;

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
