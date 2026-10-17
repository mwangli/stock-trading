ALTER TABLE strategy_config
    MODIFY COLUMN minimum_listing_years DECIMAL(5, 2) NOT NULL DEFAULT 0.50;

UPDATE strategy_config
SET minimum_listing_years = 0.50
WHERE minimum_listing_years = 0;
