package com.github.enerccio.lingollm.instruct.fixture.verify;

import com.github.enerccio.lingollm.domain.traits.Extendable;

/**
 * Inherits {@code render} from {@link VerifyTarget}: decorators for it are registered for the superclass.
 */
@Extendable
public class VerifyChild extends VerifyTarget {

    public void extra() {
    }
}
