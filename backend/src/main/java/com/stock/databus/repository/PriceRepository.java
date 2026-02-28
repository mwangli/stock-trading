package com.stock.databus.repository;

import com.stock.databus.entity.StockPrice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends MongoRepository<StockPrice, String> {

    List<StockPrice> findByCode(String code);

    List<StockPrice> findByCodeOrderByDateAsc(String code);

    List<StockPrice> findByCodeAndDateBetweenOrderByDateAsc(String code, LocalDate startDate, LocalDate endDate);

    Optional<StockPrice> findFirstByCodeOrderByDateDesc(String code);

    void deleteByCode(String code);
}
