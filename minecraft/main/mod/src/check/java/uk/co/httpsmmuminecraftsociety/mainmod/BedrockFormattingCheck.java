package uk.co.httpsmmuminecraftsociety.mainmod;

public final class BedrockFormattingCheck {
    public static void main(String[] args) {
        String result = BedrockFormatting.toDiscord("&kAb 9&rZ");
        assert result.matches("([a-z][\u0334-\u0338]{3}){2} ([a-z][\u0334-\u0338]{3})Z") : result;
        assert result.charAt(0) != 'a' : result;
        assert result.charAt(4) != 'b' : result;
        assert BedrockFormatting.toDiscord("&l&kA&rZ").matches("\\*\\*[a-z][\u0334-\u0338]{3}\\*\\*Z");
        assert BedrockFormatting.toDiscord("ordinary text").equals("ordinary text");
        System.out.println("Discord obscured formatting checks passed.");
    }
}
