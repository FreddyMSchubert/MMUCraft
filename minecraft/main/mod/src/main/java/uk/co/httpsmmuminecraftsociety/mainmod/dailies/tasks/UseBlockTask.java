package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class UseBlockTask extends CountedTask {
    private final String block;
    public UseBlockTask(Block block) {
        super("use_block:" + DailyTargetId.of(block), "Used", "times");
        this.block = DailyTargetId.of(block);
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.USE_BLOCK && event.subject().equals(block);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("block", block); }
}
