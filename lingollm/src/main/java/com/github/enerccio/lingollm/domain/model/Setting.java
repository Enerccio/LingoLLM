package com.github.enerccio.lingollm.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.annotations.DiscriminatorOptions;

@Entity
@Table(name = "settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("DTYPE")
@DiscriminatorOptions(insert = true)
public class Setting extends ExtendableEntity {

    public Setting() {
        this.dtype = this.getClass().getSimpleName();
    }

    @Column(name = "DTYPE", length = 255, updatable = false)
    protected String dtype;

    @Column(length = 64)
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
