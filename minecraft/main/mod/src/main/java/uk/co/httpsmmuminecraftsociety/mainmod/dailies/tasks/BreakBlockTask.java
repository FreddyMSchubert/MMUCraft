package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BreakBlockTask extends CountedTask {
    private final String block;
    private final String blockName;

    public BreakBlockTask(Block block, String blockName, String emoji, int min, int max, double rewardPerBlock) {
        super("break:" + DailyTargetId.of(block), emoji, min, max, rewardPerBlock, "Mined", blockName.toLowerCase());
        this.block = DailyTargetId.of(block);
        this.blockName = blockName;
    }

    @Override protected String name(int count) { return "Mine " + blockName; }
    @Override protected String description(int count) { return "Mine " + count + " " + blockName.toLowerCase() + "."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREAK_BLOCK && event.subject().equals(block);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("block", block); }
}
