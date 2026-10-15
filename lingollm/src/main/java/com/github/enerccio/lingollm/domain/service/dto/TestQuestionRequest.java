package com.github.enerccio.lingollm.domain.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One question the model is asked to write: what kind it is and where in the test it goes. A list of them is put into
 * the prompt (as JSON) so that the model writes exactly these questions, in this order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestQuestionRequest {

    private int number;
    private String section;
    private String type;
    private String notes;

    public TestQuestionRequest() {
    }

    /**
     * @param section the part of the test the question belongs to
     * @param type    the kind of the question, {@code TestQuestionHandler.getLlmTypeName()}
     * @param notes   something extra for the model to know about this question, null for nothing
     */
    public TestQuestionRequest(String section, String type, String notes) {
        this.section = section;
        this.type = type;
        this.notes = notes;
    }

    /** Position in the test, from 1; filled in when the list is put together. */
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
