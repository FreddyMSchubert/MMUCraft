package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface TickCallbackCharm extends Charm
{
    void onTick(ServerPlayer player, ServerLevel level);
}
