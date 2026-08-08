package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class UseBlockTask extends CountedTask {
    private final String block;
    private final String blockName;

    public UseBlockTask(Block block, String blockName, String emoji, int min, int max, double rewardPerUse) {
        super("use_block:" + DailyTargetId.of(block), emoji, min, max, rewardPerUse, "Used", "times");
        this.block = DailyTargetId.of(block);
        this.blockName = blockName;
    }

    @Override protected String name(int count) { return "Use a " + blockName; }
    @Override protected String description(int count) { return "Right-click a " + blockName.toLowerCase() + " " + count + " times."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_BLOCK && event.subject().equals(block);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("block", block); }
}
