package com.github.enerccio.lingollm.domain.service.impl.inference;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;

/**
 * Local token count with the OpenAI tokenizer. Close for most models but not exact: used for the limits checked before
 * a request and for the usage of a request whose provider did not report it.
 */
public final class TokenEstimator {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry().getEncodingForModel(ModelType.GPT_4O);

    private TokenEstimator() {
    }

    public static long count(String text) {
        return text == null || text.isEmpty() ? 0 : ENCODING.countTokens(text);
    }
}
