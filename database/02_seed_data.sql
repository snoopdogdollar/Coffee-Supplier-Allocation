-- Coffee Supplier Allocation: reproducible DEMO data, not production data.
-- Run the WHOLE file in pgAdmin after 01_schema.sql, on EMPTY project tables.
-- No DROP/TRUNCATE/DELETE, no fixed identity IDs, no sequence reset.
-- A second run refuses to change anything. On error execute ROLLBACK;
-- before attempting another command in the same pgAdmin session.
-- Fixture conventions ONLY: kg, VND/kg, UTC timestamps, ISO weeks 41-43/2026.
-- OPEN / COMPLETED / CONFIRMED are fixture labels, not an approved state machine.
-- Historical allocations are handcrafted examples, NOT algorithm output.
-- supplier_scores intentionally stays empty until scoring rules are agreed.

BEGIN;
SET LOCAL search_path TO public, pg_catalog;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL lock_timeout = '5s';

-- Serialize seed attempts and prevent writes while checking the empty baseline.
LOCK TABLE coffee_shops, suppliers, coffee_products, supplier_products,
    supplier_weekly_supply, demands, demand_items, recommendation_runs,
    supplier_scores, allocations, orders, order_items, supplier_reviews
    IN SHARE ROW EXCLUSIVE MODE;

DO $seed$
DECLARE
    table_name TEXT;
    has_rows BOOLEAN;
    shop_ids BIGINT[] := ARRAY[]::BIGINT[];
    supplier_ids BIGINT[] := ARRAY[]::BIGINT[];
    product_ids BIGINT[] := ARRAY[]::BIGINT[];
    historical_supply_ids BIGINT[] := ARRAY[]::BIGINT[];
    shop_names TEXT[] := ARRAY['Demo Riverside Coffee', 'Demo Morning Cup',
        'Demo Campus Coffee', 'Demo Garden Cafe', 'Demo Corner Coffee'];
    supplier_names TEXT[] := ARRAY['Demo Highland Beans', 'Demo Valley Coffee',
        'Demo Central Robusta', 'Demo Sunrise Supply', 'Demo New Harvest',
        'Demo Blend House', 'Demo Small Batch', 'Demo Paused Supplier'];
    product_names TEXT[] := ARRAY['Demo Arabica', 'Demo Robusta',
        'Demo Espresso Blend', 'Demo Specialty Arabica', 'Demo Decaf'];
    product_varieties TEXT[] := ARRAY['Arabica', 'Robusta', 'Blend', 'Arabica', 'Decaf'];
    -- Array indexes are fixture positions, NEVER assumed database IDs.
    link_suppliers INT[] := ARRAY[1,2,3,4,5,6,7,8,1,2,3,4];
    link_products INT[] := ARRAY[1,1,2,2,1,3,4,5,3,4,5,4];
    capacities NUMERIC[] := ARRAY[30,40,100,80,20,40,0,100,25,10,30,20];
    prices NUMERIC[] := ARRAY[120000,125000,80000,82000,118000,100000,
        150000,200000,105000,160000,190000,155000];
    history_qty NUMERIC[] := ARRAY[10,15,20,25];
    quality_flags BOOLEAN[] := ARRAY[TRUE,TRUE,FALSE,TRUE];
    quantity_flags BOOLEAN[] := ARRAY[TRUE,TRUE,TRUE,FALSE];
    on_time_flags BOOLEAN[] := ARRAY[TRUE,FALSE,TRUE,FALSE];
    i INT;
    w INT;
    row_id BIGINT;
    sp_id BIGINT;
    demand_id_value BIGINT;
    item_id_value BIGINT;
    run_id_value BIGINT;
    allocation_id_value BIGINT;
    order_id_value BIGINT;
    capacity NUMERIC;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['coffee_shops', 'suppliers', 'coffee_products',
        'supplier_products', 'supplier_weekly_supply', 'demands', 'demand_items',
        'recommendation_runs', 'supplier_scores', 'allocations', 'orders',
        'order_items', 'supplier_reviews']
    LOOP
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I)', table_name) INTO has_rows;
        IF has_rows THEN
            RAISE EXCEPTION 'Seed stopped: table % already contains data. No demo data was added. Use a separate empty demo database; do not delete existing work.', table_name;
        END IF;
    END LOOP;

    -- 1-3. Master records. Names are fictional; optional contact fields stay NULL.
    FOR i IN 1..5 LOOP
        INSERT INTO coffee_shops(name, created_at)
        VALUES (shop_names[i], TIMESTAMP '2026-10-01 08:00:00') RETURNING shop_id INTO row_id;
        shop_ids := array_append(shop_ids, row_id);
        INSERT INTO coffee_products(name, description, unit, variety, created_at)
        VALUES (product_names[i], 'Fictional seed product; all quantities in kg.',
            'kg', product_varieties[i], TIMESTAMP '2026-10-01 08:00:00')
        RETURNING product_id INTO row_id;
        product_ids := array_append(product_ids, row_id);
    END LOOP;
    FOR i IN 1..8 LOOP
        INSERT INTO suppliers(name, is_active, created_at)
        VALUES (supplier_names[i], i <> 8, TIMESTAMP '2026-10-01 08:00:00')
        RETURNING supplier_id INTO row_id;
        supplier_ids := array_append(supplier_ids, row_id);
    END LOOP;

    -- 4-5. Twelve offers, each with three separate weeks of capacity.
    -- Link 11 (supplier 3 / Decaf) is inactive. Supplier 8 is also inactive.
    -- Current week 42 has zero capacity on link 7; week 43 has more capacity
    -- to demonstrate that future-week supply must not fill week-42 demands.
    FOR i IN 1..12 LOOP
        INSERT INTO supplier_products(supplier_id, product_id, is_active, created_at)
        VALUES (supplier_ids[link_suppliers[i]], product_ids[link_products[i]],
            i <> 11, TIMESTAMP '2026-10-01 08:00:00')
        RETURNING supplier_product_id INTO sp_id;
        FOR w IN 41..43 LOOP
            capacity := capacities[i] + CASE WHEN w = 43 THEN 50 ELSE 0 END;
            INSERT INTO supplier_weekly_supply(supplier_product_id, week, year,
                initial_quantity, remaining_quantity, unit_price, created_at)
            VALUES (sp_id, w, 2026, capacity, capacity, prices[i], TIMESTAMP '2026-10-02 08:00:00')
            RETURNING supply_id INTO row_id;
            IF w = 41 THEN historical_supply_ids := array_append(historical_supply_ids, row_id); END IF;
        END LOOP;
    END LOOP;

    -- 6-13. Four COMPLETED transactions in ISO week 41 (Oct 5-11).
    -- Run Oct 5 -> order Oct 6 -> review Oct 9. No future review used in a score.
    FOR i IN 1..4 LOOP
        INSERT INTO demands(shop_id, request_date, required_week, required_year, status, note, created_at)
        VALUES (shop_ids[i], DATE '2026-10-05', 41, 2026, 'COMPLETED',
            'SEED v1: completed historical demand ' || i, TIMESTAMP '2026-10-05 08:00:00')
        RETURNING demand_id INTO demand_id_value;
        INSERT INTO demand_items(demand_id, product_id, quantity, note)
        VALUES (demand_id_value, product_ids[link_products[i]], history_qty[i], 'SEED v1: history')
        RETURNING demand_item_id INTO item_id_value;
        INSERT INTO recommendation_runs(demand_item_id, run_time, algorithm_version, status, note)
        VALUES (item_id_value, TIMESTAMP '2026-10-05 09:00:00', 'seed-manual-v1', 'COMPLETED',
            'Handcrafted fixture; no scoring algorithm was executed.')
        RETURNING run_id INTO run_id_value;
        UPDATE supplier_weekly_supply
        SET remaining_quantity = remaining_quantity - history_qty[i]
        WHERE supply_id = historical_supply_ids[i] AND remaining_quantity >= history_qty[i];
        IF NOT FOUND THEN RAISE EXCEPTION 'Historical supply deduction failed for fixture %', i; END IF;
        INSERT INTO allocations(demand_item_id, supplier_id, run_id, supply_id,
            allocated_quantity, allocated_price, status, created_at)
        VALUES (item_id_value, supplier_ids[link_suppliers[i]], run_id_value,
            historical_supply_ids[i], history_qty[i], prices[i], 'CONFIRMED', TIMESTAMP '2026-10-06 08:00:00')
        RETURNING allocation_id INTO allocation_id_value;
        INSERT INTO orders(shop_id, supplier_id, allocation_id, order_date, status, note, created_at)
        VALUES (shop_ids[i], supplier_ids[link_suppliers[i]], allocation_id_value,
            DATE '2026-10-06', 'COMPLETED', 'SEED v1: completed before review on Oct 9',
            TIMESTAMP '2026-10-06 08:00:00') RETURNING order_id INTO order_id_value;
        INSERT INTO order_items(order_id, product_id, quantity, unit_price, note)
        VALUES (order_id_value, product_ids[link_products[i]], history_qty[i], prices[i], 'SEED v1: history');
        -- total_price is generated by the schema; never inserted manually.
        INSERT INTO supplier_reviews(order_id, supplier_id, quality_ok, quantity_ok, on_time_ok, comment, created_at)
        VALUES (order_id_value, supplier_ids[link_suppliers[i]], quality_flags[i], quantity_flags[i],
            on_time_flags[i], 'Fictional historical review for scoring experiments.', TIMESTAMP '2026-10-09 10:00:00');
    END LOOP;

    -- Open week-42 demands (Oct 12-18). No allocations or scores are precomputed.
    FOR i IN 1..5 LOOP
        INSERT INTO demands(shop_id, request_date, required_week, required_year, status, note, created_at)
        VALUES (shop_ids[i], DATE '2026-10-12', 42, 2026, 'OPEN',
            'SEED v1: open matching scenario ' || i, TIMESTAMP '2026-10-12 08:00:00' + (i - 1) * INTERVAL '1 minute')
        RETURNING demand_id INTO demand_id_value;
        IF i = 1 THEN
            INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
                (demand_id_value, product_ids[1], 50, 'Competes with Morning Cup for Arabica'),
                (demand_id_value, product_ids[2], 10, 'Small Robusta demand');
        ELSIF i = 2 THEN
            INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
                (demand_id_value, product_ids[1], 60, 'Arabica total demand 110 kg; eligible supply 90 kg');
        ELSIF i = 3 THEN
            INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
                (demand_id_value, product_ids[2], 120, 'Needs more than any single Robusta supplier can provide');
        ELSIF i = 4 THEN
            INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
                (demand_id_value, product_ids[3], 45, 'Blend: two suppliers needed; total eligible supply 65 kg'),
                (demand_id_value, product_ids[4], 40, 'Specialty shortage: 40 kg demand vs 30 kg eligible supply');
        ELSE
            INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
                (demand_id_value, product_ids[5], 10, 'Decaf exists, but supplier/offer inactive; no eligible supply');
        END IF;
    END LOOP;

    -- Internal consistency checks happen BEFORE commit.
    IF EXISTS (
        SELECT 1 FROM supplier_weekly_supply sw
        LEFT JOIN (SELECT supply_id, sum(allocated_quantity) AS used FROM allocations GROUP BY supply_id) a
          ON a.supply_id = sw.supply_id
        WHERE sw.remaining_quantity <> sw.initial_quantity - coalesce(a.used, 0)
    ) THEN RAISE EXCEPTION 'Seed supply balances do not match allocations'; END IF;
    IF EXISTS (
        SELECT 1 FROM demand_items di JOIN allocations a USING (demand_item_id)
        GROUP BY di.demand_item_id, di.quantity HAVING sum(a.allocated_quantity) > di.quantity
    ) THEN RAISE EXCEPTION 'Seed overallocates a demand item'; END IF;
    IF EXISTS (
        SELECT 1 FROM allocations a
        JOIN supplier_weekly_supply sw USING (supply_id)
        JOIN supplier_products sp USING (supplier_product_id)
        JOIN demand_items di USING (demand_item_id)
        JOIN demands d USING (demand_id)
        WHERE a.supplier_id <> sp.supplier_id OR di.product_id <> sp.product_id
           OR d.required_week <> sw.week OR d.required_year <> sw.year
    ) THEN RAISE EXCEPTION 'Seed allocation references wrong supplier, product or week'; END IF;
    RAISE NOTICE 'Seed ready: 5 shops, 8 suppliers, 5 products, 36 weekly supplies, 5 open demands, 4 historical orders/reviews. Scores intentionally empty.';
END $seed$;

COMMIT;

-- Visible row counts in pgAdmin Data Output. The data above is PERSISTENT.
SELECT 'coffee_shops' AS table_name, count(*) AS row_count FROM coffee_shops
UNION ALL SELECT 'suppliers', count(*) FROM suppliers
UNION ALL SELECT 'coffee_products', count(*) FROM coffee_products
UNION ALL SELECT 'supplier_products', count(*) FROM supplier_products
UNION ALL SELECT 'supplier_weekly_supply', count(*) FROM supplier_weekly_supply
UNION ALL SELECT 'demands', count(*) FROM demands
UNION ALL SELECT 'demand_items', count(*) FROM demand_items
UNION ALL SELECT 'recommendation_runs', count(*) FROM recommendation_runs
UNION ALL SELECT 'supplier_scores', count(*) FROM supplier_scores
UNION ALL SELECT 'allocations', count(*) FROM allocations
UNION ALL SELECT 'orders', count(*) FROM orders
UNION ALL SELECT 'order_items', count(*) FROM order_items
UNION ALL SELECT 'supplier_reviews', count(*) FROM supplier_reviews;
