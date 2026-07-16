package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

public enum FishFurnaceResult {
    WHITE_MEAT("white_meat", "cooked-white-fish"),
    RED_MEAT("red_meat", "cooked-red-fish"),
    CRAB_CLAW("crab_claw", "crab-claw"),
    TENTACLE("tentacle", "tentacle");

    private final String jsonValue;
    private final String itemId;

    FishFurnaceResult(String jsonValue, String itemId) {
        this.jsonValue = jsonValue;
        this.itemId = itemId;
    }

    public String itemId() {
        return itemId;
    }

    public static FishFurnaceResult fromJsonValue(String value) {
        for (FishFurnaceResult result : values()) {
            if (result.jsonValue.equals(value)) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown fish furnace result: " + value);
    }
}
