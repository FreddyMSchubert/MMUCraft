package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class PlayNoteBlockTask extends CountedTask {
    private final String instrument;
    public PlayNoteBlockTask(NoteBlockInstrument instrument) {
        super("note_block:" + instrument.getSerializedName(), "Played", "notes");
        this.instrument = instrument.getSerializedName();
    }

    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLAY_NOTE_BLOCK && event.subject().equals(instrument);
    }
    @Override protected void addTaskData(JsonObject task) { task.addProperty("instrument", instrument); }
}
