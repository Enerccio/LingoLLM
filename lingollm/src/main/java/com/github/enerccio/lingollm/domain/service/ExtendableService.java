package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.repository.ExtendableRepository;

public interface ExtendableService<T extends ExtendableEntity, R extends ExtendableRepository<T>> extends OwnedService<T, R> {

}
