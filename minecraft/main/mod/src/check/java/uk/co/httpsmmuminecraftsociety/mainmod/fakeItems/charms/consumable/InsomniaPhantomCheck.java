package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

public final class InsomniaPhantomCheck {
    public static void main(String[] args) {
        assert PotionOfInsomniaCharm.minionCount(0) == 4;
        assert PotionOfInsomniaCharm.minionCount(1) == 4;
        assert PotionOfInsomniaCharm.minionCount(3) == 12;
        assert PotionOfInsomniaCharm.minionCount(5) == 20;
        assert PotionOfInsomniaCharm.minionCount(6) == 20;
    }
}
