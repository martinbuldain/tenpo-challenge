package com.tenpo.challenge.service;

import com.tenpo.challenge.domain.dto.CallHistoryDto;
import com.tenpo.challenge.domain.dto.PageResponse;
import com.tenpo.challenge.domain.entity.CallHistory;
import com.tenpo.challenge.domain.enums.CallStatus;
import com.tenpo.challenge.repository.CallHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CallHistoryRepository repository;

    public Mono<Void> recordCall(
            String endpoint,
            String httpMethod,
            String requestParams,
            String responseBody,
            String errorMessage,
            int httpStatus,
            String clientIp) {

        return Mono.fromCallable(() -> {
                CallStatus status = (httpStatus >= 200 && httpStatus < 300)
                    ? CallStatus.SUCCESS
                    : CallStatus.ERROR;

                return CallHistory.builder()
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .requestParams(requestParams)
                    .responseBody(responseBody)
                    .errorMessage(truncate(errorMessage, 1000))
                    .httpStatus(httpStatus)
                    .status(status)
                    .clientIp(clientIp)
                    .build();
            })
            .flatMap(repository::save)
            .doOnSuccess(saved -> log.debug("Historial guardado [id={}, endpoint={}, status={}]",
                saved.getId(), endpoint, httpStatus))
            .doOnError(ex -> log.error("Error al guardar el historial [endpoint={}, status={}]: {}",
                endpoint, httpStatus, ex.getMessage()))
            .onErrorResume(ex -> Mono.empty())
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public Mono<PageResponse<CallHistoryDto>> getHistory(int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        var pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Mono<Long> countMono   = repository.count();
        Mono<List<CallHistoryDto>> contentMono = repository.findAllBy(pageable)
            .map(CallHistoryDto::from)
            .collectList();

        return Mono.zip(countMono, contentMono)
            .map(tuple -> {
                long totalElements = tuple.getT1();
                List<CallHistoryDto> content = tuple.getT2();
                int totalPages = (int) Math.ceil((double) totalElements / safeSize);
                return new PageResponse<>(
                    content, page, safeSize, totalElements, totalPages,
                    page == 0, page >= totalPages - 1
                );
            });
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
