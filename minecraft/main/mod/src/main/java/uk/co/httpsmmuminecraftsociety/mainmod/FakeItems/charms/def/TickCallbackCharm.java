package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface TickCallbackCharm
{
    void onTick(ServerPlayer player, ServerLevel level);
}
