package com.github.enerccio.lingollm.bound;

public interface Migration {

    int migrate(int cversion, MigrationType migrationType) throws Exception;

    enum MigrationType {
        DB, APP
    }

}
