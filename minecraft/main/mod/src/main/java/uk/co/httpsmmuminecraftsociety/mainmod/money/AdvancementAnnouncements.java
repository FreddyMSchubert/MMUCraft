package uk.co.httpsmmuminecraftsociety.mainmod.money;

public final class AdvancementAnnouncements {
    private AdvancementAnnouncements() {}

    public static boolean broadcast(String namespace, boolean vanillaBroadcast, boolean showAdvancements, int reward) {
        return "minecraft".equals(namespace)
                ? vanillaBroadcast && showAdvancements
                : reward >= 30;
    }
}
