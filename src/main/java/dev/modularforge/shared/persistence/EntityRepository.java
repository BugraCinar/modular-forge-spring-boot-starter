package dev.modularforge.shared.persistence;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Store-independent operations exposed to feature services. */
@NoRepositoryBean
public interface EntityRepository<T> extends ListCrudRepository<T, Long>, ListPagingAndSortingRepository<T, Long> {
    default <S extends T> S saveAndFlush(S entity) { return save(entity); }
    default void flush() { }
}
