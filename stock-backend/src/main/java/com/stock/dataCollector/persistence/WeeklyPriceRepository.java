package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockPriceWeekly;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyPriceRepository extends MongoRepository<StockPriceWeekly, String> {

    List<StockPriceWeekly> findByCodeOrderByDateAsc(String code);
    Optional<StockPriceWeekly> findTopByCodeOrderByDateDesc(String code);
    List<StockPriceWeekly> findByCodeAndDateBetweenOrderByDateAsc(String code, LocalDate startDate, LocalDate endDate);
    void deleteByCode(String code);
    boolean existsByCodeAndDate(String code, LocalDate date);
    boolean existsByCode(String code);
    long count();
}
