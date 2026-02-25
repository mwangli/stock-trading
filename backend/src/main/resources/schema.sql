-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tsin VARCHAR(50) NOT NULL UNIQUE,
    product_url VARCHAR(500),
    main_category VARCHAR(100),
    lowest_category VARCHAR(100),
    category_id BIGINT,
    product_title VARCHAR(500) NOT NULL,
    subtitle VARCHAR(500),
    description TEXT,
    whats_in_box TEXT,
    brand VARCHAR(100),
    warranty_type VARCHAR(50),
    warranty_period INT,
    image_urls VARCHAR(2000),
    rating DECIMAL(2,1),
    review_count INT,
    price DECIMAL(10,2),
    currency VARCHAR(10) DEFAULT 'ZAR',
    video_url VARCHAR(500),
    collected_at TIMESTAMP NOT NULL,
    task_id BIGINT,
    is_filtered BOOLEAN DEFAULT FALSE,
    filter_reason VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分类表
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    level INT,
    url VARCHAR(500),
    source VARCHAR(50) DEFAULT 'takealot',
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 采集任务表
CREATE TABLE IF NOT EXISTS collection_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    category_filter VARCHAR(500),
    brand_filter TEXT,
    max_products INT DEFAULT 100,
    actual_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    filtered_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INT,
    error_message TEXT,
    cron_expression VARCHAR(100),
    next_run_time TIMESTAMP,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 采集日志表
CREATE TABLE IF NOT EXISTS collection_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    tsin VARCHAR(50),
    product_title VARCHAR(500),
    product_url VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    message TEXT,
    retry_count INT DEFAULT 0,
    response_time_ms INT,
    proxy_used VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 品牌黑名单表
CREATE TABLE IF NOT EXISTS brand_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name VARCHAR(100) NOT NULL UNIQUE,
    brand_type VARCHAR(50),
    reason VARCHAR(200),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化品牌黑名单数据 (使用INSERT ... ON DUPLICATE KEY UPDATE for MySQL)
INSERT INTO brand_blacklist (brand_name, brand_type, reason) VALUES
('apple', 'ELECTRONICS', '知名品牌，避免侵权'),
('samsung', 'ELECTRONICS', '知名品牌，避免侵权'),
('huawei', 'ELECTRONICS', '知名品牌，避免侵权'),
('xiaomi', 'ELECTRONICS', '知名品牌，避免侵权'),
('google', 'ELECTRONICS', '知名品牌，避免侵权'),
('sony', 'ELECTRONICS', '知名品牌，避免侵权'),
('lg', 'ELECTRONICS', '知名品牌，避免侵权'),
('dell', 'ELECTRONICS', '知名品牌，避免侵权'),
('hp', 'ELECTRONICS', '知名品牌，避免侵权'),
('acer', 'ELECTRONICS', '知名品牌，避免侵权'),
('msi', 'ELECTRONICS', '知名品牌，避免侵权'),
('microsoft', 'ELECTRONICS', '知名品牌，避免侵权'),
('canon', 'ELECTRONICS', '知名品牌，避免侵权'),
('nikon', 'ELECTRONICS', '知名品牌，避免侵权'),
('fujifilm', 'ELECTRONICS', '知名品牌，避免侵权'),
('gopro', 'ELECTRONICS', '知名品牌，避免侵权'),
('dji', 'ELECTRONICS', '知名品牌，避免侵权'),
('beats', 'AUDIO', '知名品牌，避免侵权'),
('bose', 'AUDIO', '知名品牌，避免侵权'),
('jbl', 'AUDIO', '知名品牌，避免侵权'),
('sennheiser', 'AUDIO', '知名品牌，避免侵权'),
('airpods', 'AUDIO', '知名品牌，避免侵权'),
('playstation', 'GAMING', '知名品牌，避免侵权'),
('xbox', 'GAMING', '知名品牌，避免侵权'),
('nintendo', 'GAMING', '知名品牌，避免侵权'),
('logitech', 'GAMING', '知名品牌，避免侵权'),
('razer', 'GAMING', '知名品牌，避免侵权'),
('nike', 'FASHION', '知名品牌，避免侵权'),
('adidas', 'FASHION', '知名品牌，避免侵权'),
('puma', 'FASHION', '知名品牌，避免侵权'),
('gucci', 'FASHION', '知名品牌，避免侵权'),
('prada', 'FASHION', '知名品牌，避免侵权'),
('zara', 'FASHION', '知名品牌，避免侵权'),
('hm', 'FASHION', '知名品牌，避免侵权'),
('rolex', 'WATCHES', '知名品牌，避免侵权'),
('omega', 'WATCHES', '知名品牌，避免侵权'),
('cartier', 'WATCHES', '知名品牌，避免侵权'),
('tiffany', 'WATCHES', '知名品牌避免侵权'),
('casio', 'WATCHES', '知名品牌，避免侵权'),
('lego', 'TOYS', '知名品牌，避免侵权'),
('disney', 'ENTERTAINMENT', '知名品牌，避免侵权'),
('coca-cola', 'FOOD', '知名品牌，避免侵权'),
('nestle', 'FOOD', '知名品牌，避免侵权')
ON DUPLICATE KEY UPDATE brand_name=brand_name;
