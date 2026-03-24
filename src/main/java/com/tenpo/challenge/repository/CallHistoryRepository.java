package com.tenpo.challenge.repository;

import com.tenpo.challenge.domain.entity.CallHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CallHistoryRepository extends ReactiveCrudRepository<CallHistory, Long> {

    Flux<CallHistory> findAllBy(Pageable pageable);
}
