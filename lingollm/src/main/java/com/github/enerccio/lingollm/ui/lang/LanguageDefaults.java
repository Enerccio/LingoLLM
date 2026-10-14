package com.github.enerccio.lingollm.ui.lang;

public class LanguageDefaults {

    public static LanguageDefaults JAPANESE;
    public static LanguageDefaults GERMAN;

    private String freeChatTemplate;

    static {
        {
            JAPANESE = new LanguageDefaults();
            JAPANESE.freeChatTemplate = """
            """;
        }
        {
            GERMAN = new LanguageDefaults();
            GERMAN.freeChatTemplate = """
            """;
        }
    }

}
