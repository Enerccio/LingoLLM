package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.impl.AI;

public interface InferenceServices {

    InferenceService forAI(AI ai) throws Exception;

    /**
     * The functions the LLM can call, compiled once per interface. See {@link FunctionRegistry}.
     */
    FunctionRegistry getFunctionRegistry();

    /**
     * Lets an extension change the role the results of function calls are sent with ({@link #DEFAULT_TOOL_ROLE}), for
     * models whose chat template expects another name. Remove it again when the extension is unloaded.
     */
    void addToolRoleResolver(ToolRoleResolver resolver);

    void removeToolRoleResolver(ToolRoleResolver resolver);

    /**
     * The role to send the results of function calls to the AI's model with: the answer of the first resolver that
     * has one, otherwise {@link #DEFAULT_TOOL_ROLE}.
     */
    String resolveToolRole(AI ai);

    /** The role of the results of function calls in the OpenAI chat API. */
    String DEFAULT_TOOL_ROLE = "tool";

    interface ToolRoleResolver {

        /**
         * @return the role for the AI's model, null to leave it to the other resolvers
         */
        String toolRole(AI ai);

    }

    interface InferenceProvider {

        InferenceService create(AI ai) throws Exception;

    }

}
