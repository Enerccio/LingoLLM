package com.github.enerccio.lingollm.instruct.fixture.verify;

import com.github.enerccio.lingollm.domain.traits.Extendable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the extensions of {@link VerifyFixtures} decorate.
 */
@Extendable
public class VerifyTarget {

    private String title = "";
    private final List<String> items = new ArrayList<>();

    public String render(String name, int count, List<String> values) {
        StringBuilder out = new StringBuilder(title);
        Object result = out;
        for (int i = 0; i < count; i++) {
            out.append(name).append(values.size());
        }
        items.add(out.toString());
        return String.valueOf(result);
    }

    private String helper(String prefix, int times) {
        return prefix.repeat(times);
    }

    public static void staticMethod() {
    }

    @Extendable
    public class Card {

        private final String message = "message";

        public void edit(String text) {
            // uses the outer instance, so the class keeps this$0
            title = text + message;
        }
    }
}
