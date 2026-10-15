package com.github.enerccio.lingollm.ui.lang.languages.common;

import com.github.enerccio.lingollm.domain.model.impl.Test;
import com.github.enerccio.lingollm.domain.model.impl.TestQuestionGroup;
import com.github.enerccio.lingollm.domain.service.TestQuestionGroupService;
import com.github.enerccio.lingollm.domain.service.TestService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.util.function.Supplier;

/**
 * Works with the data of a {@link TestQuestionGroup}, kept in its attributes. A group type derives from it with its
 * own data class, see {@link com.github.enerccio.lingollm.domain.factories.TestQuestionGroupFactory}.
 */
@Configurable
public class TestQuestionGroupHandler<DATA_CLASS extends TestQuestionGroupData> {
    private static final Gson gson = new GsonBuilder().create();
    private static final String GROUP_DATA = "$groupData";

    @Autowired
    private TestQuestionGroupService testQuestionGroupService;

    @Autowired
    private TestService testService;

    protected Test test;
    protected TestQuestionGroup group;

    private final Class<DATA_CLASS> type;
    private final Supplier<DATA_CLASS> empty;

    protected TestQuestionGroupHandler(Test test, TestQuestionGroup group, Class<DATA_CLASS> type, Supplier<DATA_CLASS> empty) {
        this.test = test;
        this.group = group;
        this.type = type;
        this.empty = empty;
    }

    protected DATA_CLASS createEmpty() {
        return empty.get();
    }

    protected Class<DATA_CLASS> getType() {
        return type;
    }

    protected DATA_CLASS getGroupData() {
        JsonObject attributes = group.getAttributes();
        if (!attributes.has(GROUP_DATA)) {
            return createEmpty();
        }
        return gson.fromJson(attributes.get(GROUP_DATA), getType());
    }

    protected void setGroupData(DATA_CLASS groupData) {
        group.getAttributes().remove(GROUP_DATA);
        group.getAttributes().add(GROUP_DATA, gson.toJsonTree(groupData == null ? createEmpty() : groupData));
    }

    public void setInstructions(LanguageString instructions) {
        DATA_CLASS data = getGroupData();
        data.setInstructions(instructions);
        setGroupData(data);
    }

    public void save() throws Exception {
        group = testQuestionGroupService.save(group);
    }

    public void refresh() throws Exception {
        test = testService.find(test);
        group = testQuestionGroupService.find(group);
    }

}
