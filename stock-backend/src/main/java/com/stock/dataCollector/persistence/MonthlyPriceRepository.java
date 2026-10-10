package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockPriceMonthly;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyPriceRepository extends MongoRepository<StockPriceMonthly, String> {

    List<StockPriceMonthly> findByCodeOrderByDateAsc(String code);
    Optional<StockPriceMonthly> findTopByCodeOrderByDateDesc(String code);
    List<StockPriceMonthly> findByCodeAndDateBetweenOrderByDateAsc(String code, LocalDate startDate, LocalDate endDate);
    void deleteByCode(String code);
    boolean existsByCodeAndDate(String code, LocalDate date);
    boolean existsByCode(String code);
    long count();
}
