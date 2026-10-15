package com.github.enerccio.lingollm.domain.collections;

/**
 * The levels of the Common European Framework of Reference for Languages. The {@link #getDescription() description} is
 * written for the model (to tell it what a learner of this level can and cannot do), not for the screen.
 */
public enum CEFR {
    A1("""
            Beginner. Understands and uses familiar everyday expressions and very basic phrases. Can introduce themselves \
            and ask and answer simple personal questions (where they live, people they know, things they have) if \
            the other person speaks slowly and clearly."""),
    A2("""
            Elementary. Understands sentences and frequently used expressions on immediate topics (family, shopping, \
            work, local area). Can handle simple, routine exchanges and describe their background and immediate \
            needs in simple terms."""),
    B1("""
            Intermediate. Understands the main points of clear standard speech and text on familiar matters (work, school, \
            leisure). Can deal with most situations while travelling, produce simple connected text on familiar \
            topics and describe experiences, events, hopes and short reasons for opinions."""),
    B2("""
            Upper intermediate. Understands the main ideas of complex text on concrete and abstract topics, including \
            technical discussion in their field. Can interact with a degree of fluency and spontaneity with native \
            speakers, and produce clear, detailed text on a wide range of subjects."""),
    C1("""
            Advanced. Understands a wide range of demanding, longer texts and recognises implicit meaning. Expresses \
            themselves fluently and spontaneously without much obvious searching for expressions, uses the language \
            flexibly for social, academic and professional purposes, and produces well structured, detailed text \
            on complex subjects."""),
    C2("""
            Proficient. Understands virtually everything heard or read with ease. Can summarise information from different \
            sources, reconstruct arguments coherently, and express themselves spontaneously, very fluently and \
            precisely, differentiating finer shades of meaning even in the most complex situations.""");

    private final String description;

    CEFR(String description) {
        this.description = description;
    }

    /** A short description of the level for the model. */
    public String getDescription() {
        return description;
    }
}
