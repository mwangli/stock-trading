package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockPrice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends MongoRepository<StockPrice, String> {

    List<StockPrice> findByCodeOrderByDateAsc(String code);
    Optional<StockPrice> findTopByCodeOrderByDateDesc(String code);
    List<StockPrice> findByCodeAndDateBetweenOrderByDateAsc(String code, LocalDate startDate, LocalDate endDate);
    Optional<StockPrice> findByCodeAndDate(String code, LocalDate date);
    void deleteByCode(String code);
    boolean existsByCodeAndDate(String code, LocalDate date);
    boolean existsByCode(String code);
}
