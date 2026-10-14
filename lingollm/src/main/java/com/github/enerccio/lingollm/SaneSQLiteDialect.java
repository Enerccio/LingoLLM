package com.github.enerccio.lingollm;

import org.hibernate.community.dialect.SQLiteDialect;

public class SaneSQLiteDialect extends SQLiteDialect {

    @Override
    public String getCheckCondition(String columnName, String[] values) {
        return null;
    }

    @Override
    public String getCheckCondition(String columnName, long min, long max) {
        return null;
    }
}