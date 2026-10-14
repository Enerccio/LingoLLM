package com.github.enerccio.lingollm.domain.model;

import com.github.enerccio.lingollm.domain.listener.ExtendableEntityListener;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.google.gson.JsonObject;
import jakarta.persistence.*;

@MappedSuperclass
@EntityListeners({ ExtendableEntityListener.class })
public class ExtendableEntity extends OwnedEntity {

    @Column(length = 268435456)
    private byte[] extendedContent;

    @Lob
    private String _fulltext;

    @Transient
    @ExtendedAttribute(inject = true, injectPrefix = "attributes")
    private JsonObject attributes = new JsonObject();

    public byte[] getExtendedContent() {
        return extendedContent;
    }

    public void setExtendedContent(byte[] extendedContent) {
        this.extendedContent = extendedContent;
    }

    public JsonObject getAttributes() {
        return attributes;
    }

    public void setAttributes(JsonObject attributes) {
        this.attributes = attributes;
    }

    public String get_fulltext() {
        return _fulltext;
    }

    public void set_fulltext(String _fulltext) {
        this._fulltext = _fulltext;
    }
}
