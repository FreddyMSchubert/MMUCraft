package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BreakBlockTask extends CountedTask {
    private final String block;
    public BreakBlockTask(Block block) {
        super("break:" + DailyTargetId.of(block), "Mined", "blocks");
        this.block = DailyTargetId.of(block);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BREAK_BLOCK && event.subject().equals(block);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("block", block); }
}
