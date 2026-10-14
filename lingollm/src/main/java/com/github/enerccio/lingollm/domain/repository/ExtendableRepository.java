package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.ExtendableEntity;

public interface ExtendableRepository<T extends ExtendableEntity> extends OwnedRepository<T> {

}
