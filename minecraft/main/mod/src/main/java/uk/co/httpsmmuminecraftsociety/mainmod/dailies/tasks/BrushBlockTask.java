package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.Block;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class BrushBlockTask extends CountedTask {
    private final String block;
    private final String blockName;

    public BrushBlockTask(Block block, String blockName) {
        super("brush:" + DailyTargetId.of(block), "🖌️", 4, 12, 0.5D, "Brushed", "times");
        this.block = DailyTargetId.of(block);
        this.blockName = blockName;
    }

    @Override protected String name(int count) { return "Brush " + blockName; }
    @Override protected String description(int count) { return "Brush " + blockName.toLowerCase() + " " + count + " times."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.BRUSH_BLOCK && event.subject().equals(block);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("block", block); }
}
