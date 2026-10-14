package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.repository.ExtendableRepository;
import com.github.enerccio.lingollm.domain.service.ExtendableService;

public class ExtendableServiceImpl<T extends ExtendableEntity, R extends ExtendableRepository<T>> extends OwnedServiceImpl<T, R> implements ExtendableService<T, R> {

}
