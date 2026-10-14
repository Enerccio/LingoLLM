package com.github.enerccio.lingollm.domain.model;

import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * Everything a user owns belongs to them, once deleted user is purged by cleanup, all their data is purged with them.
 */
@MappedSuperclass
@CleanupReference(field = "owner", value = Policy.OWNED_BY)
public class OwnedEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "userId", nullable = true)
    private User owner;

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
}
