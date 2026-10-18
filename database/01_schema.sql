-- Coffee Supplier Allocation - PostgreSQL schema v1
-- Source: db_schema.png supplied by the project owner.
-- Run ONCE on an empty development/test database. No DROP or destructive reset.
-- Added after schema review: demands.required_week/required_year,
-- allocations.run_id/supply_id. See README.md for assumptions and service rules.
-- All names use unquoted lowercase: PostgreSQL folds COFFEE_SHOPS to coffee_shops.

BEGIN;

-- 1. Shops buying coffee.
CREATE TABLE coffee_shops (
    shop_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    contact_person VARCHAR(100),
    phone          VARCHAR(20),
    email          VARCHAR(100),
    address        VARCHAR(255),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

-- 2. Suppliers. Contact information is optional, not globally unique.
CREATE TABLE suppliers (
    supplier_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    contact_person VARCHAR(100),
    phone          VARCHAR(20),
    email          VARCHAR(100),
    address        VARCHAR(255),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

-- 3. Product.unit defines the unit for ALL quantities for this product.
CREATE TABLE coffee_products (
    product_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    description VARCHAR(255),
    unit        VARCHAR(20) NOT NULL CHECK (btrim(unit) <> ''),
    variety     VARCHAR(50),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

-- 4. A supplier offers a given product at most once.
CREATE TABLE supplier_products (
    supplier_product_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id         BIGINT NOT NULL REFERENCES suppliers(supplier_id),
    product_id          BIGINT NOT NULL REFERENCES coffee_products(product_id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT uq_supplier_product UNIQUE (supplier_id, product_id)
);

-- 5. Weekly capacity, not physical warehouse inventory.
-- Convention: ISO week and ISO week-numbering year (not always calendar year).
CREATE TABLE supplier_weekly_supply (
    supply_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_product_id BIGINT NOT NULL REFERENCES supplier_products(supplier_product_id),
    week                INT NOT NULL,
    year                INT NOT NULL,
    initial_quantity    DECIMAL(15,2) NOT NULL,
    remaining_quantity  DECIMAL(15,2) NOT NULL,
    unit_price          DECIMAL(15,2) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT uq_weekly_supply UNIQUE (supplier_product_id, year, week),
    CONSTRAINT ck_supply_year CHECK (year BETWEEN 1 AND 9999),
    CONSTRAINT ck_supply_week CHECK (week BETWEEN 1 AND 53),
    CONSTRAINT ck_supply_iso_week CHECK (
        week <= EXTRACT(WEEK FROM make_date(year, 12, 28))
    ),
    CONSTRAINT ck_initial_quantity CHECK (
        initial_quantity >= 0 AND initial_quantity <> 'NaN'::NUMERIC
    ),
    CONSTRAINT ck_remaining_quantity CHECK (
        remaining_quantity BETWEEN 0 AND initial_quantity
        AND remaining_quantity <> 'NaN'::NUMERIC
    ),
    CONSTRAINT ck_supply_price CHECK (
        unit_price >= 0 AND unit_price <> 'NaN'::NUMERIC
    )
);

-- 6. Every item in a demand is needed in the same ISO week.
-- request_date is the date the request was made, not the delivery week.
-- No invented status vocabulary: the Java workflow must define it first.
CREATE TABLE demands (
    demand_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id       BIGINT NOT NULL REFERENCES coffee_shops(shop_id),
    request_date  DATE NOT NULL DEFAULT CURRENT_DATE,
    required_week INT NOT NULL,
    required_year INT NOT NULL,
    status        VARCHAR(20) NOT NULL CHECK (btrim(status) <> ''),
    note          VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT ck_demand_year CHECK (required_year BETWEEN 1 AND 9999),
    CONSTRAINT ck_demand_week CHECK (required_week BETWEEN 1 AND 53),
    CONSTRAINT ck_demand_iso_week CHECK (
        required_week <= EXTRACT(WEEK FROM make_date(required_year, 12, 28))
    )
);

-- 7. Multiple lines for the same product remain allowed, as in the diagram.
CREATE TABLE demand_items (
    demand_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    demand_id      BIGINT NOT NULL REFERENCES demands(demand_id),
    product_id     BIGINT NOT NULL REFERENCES coffee_products(product_id),
    quantity       DECIMAL(15,2) NOT NULL,
    note           VARCHAR(255),
    CONSTRAINT ck_demand_quantity CHECK (quantity > 0 AND quantity <> 'NaN'::NUMERIC)
);

-- 8. One run concerns one demand item; an item can have several historical runs.
CREATE TABLE recommendation_runs (
    run_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    demand_item_id    BIGINT NOT NULL REFERENCES demand_items(demand_item_id),
    run_time          TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    algorithm_version VARCHAR(50) NOT NULL CHECK (btrim(algorithm_version) <> ''),
    status            VARCHAR(20) NOT NULL CHECK (btrim(status) <> ''),
    note              VARCHAR(255),
    CONSTRAINT uq_run_demand_item UNIQUE (run_id, demand_item_id)
);

-- 9. Snapshot of scores, not a view that recalculates past results.
-- No 0..1 ceiling until normalization is agreed. DECIMAL(5,4) is from the image.
CREATE TABLE supplier_scores (
    score_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id            BIGINT NOT NULL REFERENCES recommendation_runs(run_id),
    supplier_id       BIGINT NOT NULL REFERENCES suppliers(supplier_id),
    price_score       DECIMAL(5,4) NOT NULL,
    reliability_score DECIMAL(5,4) NOT NULL,
    other_score       DECIMAL(5,4) NOT NULL,
    final_score       DECIMAL(5,4) NOT NULL,
    rank              INT NOT NULL CHECK (rank > 0),
    CONSTRAINT uq_run_supplier UNIQUE (run_id, supplier_id),
    CONSTRAINT ck_price_score CHECK (price_score >= 0 AND price_score <> 'NaN'::NUMERIC),
    CONSTRAINT ck_reliability_score CHECK (reliability_score >= 0 AND reliability_score <> 'NaN'::NUMERIC),
    CONSTRAINT ck_other_score CHECK (other_score >= 0 AND other_score <> 'NaN'::NUMERIC),
    CONSTRAINT ck_final_score CHECK (final_score >= 0 AND final_score <> 'NaN'::NUMERIC)
);

-- 10. Supply provenance and recommendation provenance are both explicit.
-- Service must validate supplier/product/week against supply_id, and enforce
-- aggregate limits under locks. FK alone does not enforce those business rules.
CREATE TABLE allocations (
    allocation_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    demand_item_id     BIGINT NOT NULL REFERENCES demand_items(demand_item_id),
    supplier_id        BIGINT NOT NULL REFERENCES suppliers(supplier_id),
    run_id             BIGINT NOT NULL,
    supply_id          BIGINT NOT NULL REFERENCES supplier_weekly_supply(supply_id),
    allocated_quantity DECIMAL(15,2) NOT NULL,
    allocated_price    DECIMAL(15,2) NOT NULL,
    status             VARCHAR(20) NOT NULL CHECK (btrim(status) <> ''),
    created_at         TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT fk_allocation_run_item FOREIGN KEY (run_id, demand_item_id)
        REFERENCES recommendation_runs(run_id, demand_item_id),
    CONSTRAINT ck_allocated_quantity CHECK (
        allocated_quantity > 0 AND allocated_quantity <> 'NaN'::NUMERIC
    ),
    CONSTRAINT ck_allocated_price CHECK (
        allocated_price >= 0 AND allocated_price <> 'NaN'::NUMERIC
    ),
    CONSTRAINT uq_allocation_supplier UNIQUE (allocation_id, supplier_id)
);

-- 11. Preserve the diagram's 1 allocation -> N orders relationship.
CREATE TABLE orders (
    order_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id       BIGINT NOT NULL REFERENCES coffee_shops(shop_id),
    supplier_id   BIGINT NOT NULL REFERENCES suppliers(supplier_id),
    allocation_id BIGINT NOT NULL,
    order_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    status        VARCHAR(20) NOT NULL CHECK (btrim(status) <> ''),
    note          VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT fk_order_allocation_supplier FOREIGN KEY (allocation_id, supplier_id)
        REFERENCES allocations(allocation_id, supplier_id),
    CONSTRAINT uq_order_supplier UNIQUE (order_id, supplier_id)
);

-- 12. Prices are snapshots. total_price is computed to prevent inconsistent totals.
-- A total may exceed DECIMAL(15,2): PostgreSQL rejects that order item, not truncates it.
CREATE TABLE order_items (
    order_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(order_id),
    product_id    BIGINT NOT NULL REFERENCES coffee_products(product_id),
    quantity      DECIMAL(15,2) NOT NULL,
    unit_price    DECIMAL(15,2) NOT NULL,
    total_price   DECIMAL(15,2) GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED,
    note          VARCHAR(255),
    CONSTRAINT ck_order_quantity CHECK (quantity > 0 AND quantity <> 'NaN'::NUMERIC),
    CONSTRAINT ck_order_price CHECK (unit_price >= 0 AND unit_price <> 'NaN'::NUMERIC)
);

-- 13. Preserve the diagram's 1 order -> N reviews; no invented star rating.
-- Eligibility (completed order, authorized shop) must be checked by the service.
CREATE TABLE supplier_reviews (
    review_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(supplier_id),
    quality_ok  BOOLEAN NOT NULL,
    quantity_ok BOOLEAN NOT NULL,
    on_time_ok  BOOLEAN NOT NULL,
    comment     VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT fk_review_order_supplier FOREIGN KEY (order_id, supplier_id)
        REFERENCES orders(order_id, supplier_id)
);

-- PostgreSQL automatically indexes PK/UNIQUE, but not referencing FK columns.
CREATE INDEX idx_supplier_products_product ON supplier_products(product_id);
CREATE INDEX idx_supply_period ON supplier_weekly_supply(year, week);
CREATE INDEX idx_demands_shop ON demands(shop_id);
CREATE INDEX idx_demands_period ON demands(required_year, required_week);
CREATE INDEX idx_demand_items_demand ON demand_items(demand_id);
CREATE INDEX idx_demand_items_product ON demand_items(product_id);
CREATE INDEX idx_runs_item_time ON recommendation_runs(demand_item_id, run_time);
CREATE INDEX idx_scores_supplier ON supplier_scores(supplier_id);
CREATE INDEX idx_allocations_item ON allocations(demand_item_id);
CREATE INDEX idx_allocations_run_item ON allocations(run_id, demand_item_id);
CREATE INDEX idx_allocations_supply ON allocations(supply_id);
CREATE INDEX idx_allocations_supplier ON allocations(supplier_id);
CREATE INDEX idx_orders_shop ON orders(shop_id);
CREATE INDEX idx_orders_supplier ON orders(supplier_id);
CREATE INDEX idx_orders_allocation_supplier ON orders(allocation_id, supplier_id);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_reviews_order_supplier ON supplier_reviews(order_id, supplier_id);
CREATE INDEX idx_reviews_supplier_time ON supplier_reviews(supplier_id, created_at);

COMMENT ON COLUMN allocations.allocated_price IS
    'v1 assumption: agreed price PER UNIT, snapshot when allocation is confirmed.';
COMMENT ON COLUMN supplier_weekly_supply.remaining_quantity IS
    'Stored balance. Supply deduction and allocation/order writes require one service transaction.';
COMMENT ON COLUMN recommendation_runs.run_time IS
    'Historical cutoff convention: scoring only uses eligible reviews existing at this time.';
COMMENT ON TABLE supplier_reviews IS
    'Three Boolean review criteria. Review lifecycle/duplicate policy remains a service design decision.';

COMMIT;
