-- Rolling demonstration fixture, NOT production data or a migration.
-- Run the WHOLE file after 01_schema.sql in a NEW, EMPTY demo database.
-- Use this INSTEAD OF 02_seed_data.sql; never run both in the same database.
-- Weeks come from the database clock in Asia/Ho_Chi_Minh, including ISO years.
-- No reset, deletion, assumed identity IDs, preconfirmed allocations or fake scores.
-- Re-running on populated tables refuses all inserts. After an error, ROLLBACK;

BEGIN;
SET LOCAL search_path TO public, pg_catalog;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL lock_timeout = '5s';

LOCK TABLE coffee_shops, suppliers, coffee_products, supplier_products,
    supplier_weekly_supply, demands, demand_items, recommendation_runs,
    supplier_scores, allocations, orders, order_items, supplier_reviews
    IN SHARE ROW EXCLUSIVE MODE;

DO $demo$
DECLARE
    table_name TEXT;
    has_rows BOOLEAN;
    business_date DATE := (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE;
    current_monday DATE := date_trunc('week', business_date::TIMESTAMP)::DATE;
    delivery_date DATE;
    shop_ids BIGINT[] := ARRAY[]::BIGINT[];
    supplier_ids BIGINT[] := ARRAY[]::BIGINT[];
    product_ids BIGINT[] := ARRAY[]::BIGINT[];
    shop_names TEXT[] := ARRAY['Demo Riverside Coffee', 'Demo Morning Cup'];
    supplier_names TEXT[] := ARRAY['Demo Highland Beans', 'Demo Valley Coffee', 'Demo New Harvest'];
    product_names TEXT[] := ARRAY['Demo Arabica', 'Demo Robusta', 'Demo Decaf'];
    link_suppliers INT[] := ARRAY[1, 2, 3, 1, 2, 3];
    link_products INT[] := ARRAY[1, 1, 1, 2, 2, 3];
    capacities NUMERIC[] := ARRAY[30, 40, 20, 100, 80, 100];
    prices NUMERIC[] := ARRAY[120000, 125000, 118000, 80000, 82000, 190000];
    row_id BIGINT;
    offering_id BIGINT;
    demand_id_value BIGINT;
    i INT;
    week_offset INT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['coffee_shops', 'suppliers', 'coffee_products',
        'supplier_products', 'supplier_weekly_supply', 'demands', 'demand_items',
        'recommendation_runs', 'supplier_scores', 'allocations', 'orders',
        'order_items', 'supplier_reviews']
    LOOP
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I)', table_name) INTO has_rows;
        IF has_rows THEN
            RAISE EXCEPTION 'Demo seed stopped: table % already contains data. Use a new empty demo database; existing work was not changed.', table_name;
        END IF;
    END LOOP;

    FOR i IN 1..2 LOOP
        INSERT INTO coffee_shops(name) VALUES (shop_names[i]) RETURNING shop_id INTO row_id;
        shop_ids := array_append(shop_ids, row_id);
    END LOOP;
    FOR i IN 1..3 LOOP
        INSERT INTO suppliers(name) VALUES (supplier_names[i]) RETURNING supplier_id INTO row_id;
        supplier_ids := array_append(supplier_ids, row_id);
        INSERT INTO coffee_products(name, description, unit)
        VALUES (product_names[i], 'Rolling fictional demo product; quantities in kg and prices in VND/kg.', 'kg')
        RETURNING product_id INTO row_id;
        product_ids := array_append(product_ids, row_id);
    END LOOP;

    -- The Decaf offering is stopped: its saved supply is deliberately ineligible.
    FOR i IN 1..6 LOOP
        INSERT INTO supplier_products(supplier_id, product_id, is_active)
        VALUES (supplier_ids[link_suppliers[i]], product_ids[link_products[i]], i <> 6)
        RETURNING supplier_product_id INTO offering_id;
        FOR week_offset IN 0..1 LOOP
            delivery_date := current_monday + week_offset * 7;
            INSERT INTO supplier_weekly_supply(supplier_product_id, year, week,
                initial_quantity, remaining_quantity, unit_price)
            VALUES (offering_id, EXTRACT(ISOYEAR FROM delivery_date)::INT,
                EXTRACT(WEEK FROM delivery_date)::INT,
                capacities[i] + week_offset * 50, capacities[i] + week_offset * 50, prices[i]);
        END LOOP;
    END LOOP;

    -- Current-week priority follows request_date, demand ID, then item ID.
    -- 1: Arabica splits between cheap suppliers; the small Robusta line also fits.
    INSERT INTO demands(shop_id, request_date, required_year, required_week, status, note)
    VALUES (shop_ids[1], business_date, EXTRACT(ISOYEAR FROM current_monday)::INT,
        EXTRACT(WEEK FROM current_monday)::INT, 'OPEN', 'Rolling demo: first Buyer request')
    RETURNING demand_id INTO demand_id_value;
    INSERT INTO demand_items(demand_id, product_id, quantity, note) VALUES
        (demand_id_value, product_ids[1], 50, 'Arabica: 20 kg at 118000 and 30 kg at 120000 VND/kg'),
        (demand_id_value, product_ids[2], 10, 'Robusta: small request before the second shop');

    -- 2: only 40 kg of current-week Arabica remains after demand 1. No partial fill.
    INSERT INTO demands(shop_id, request_date, required_year, required_week, status, note)
    VALUES (shop_ids[2], business_date, EXTRACT(ISOYEAR FROM current_monday)::INT,
        EXTRACT(WEEK FROM current_monday)::INT, 'OPEN', 'Rolling demo: competing Arabica shortage')
    RETURNING demand_id INTO demand_id_value;
    INSERT INTO demand_items(demand_id, product_id, quantity, note)
    VALUES (demand_id_value, product_ids[1], 60, 'Needs 60 kg; 40 kg remains. Next-week supply must not be used.');

    -- 3: enough combined Robusta, but it must split across two suppliers.
    INSERT INTO demands(shop_id, request_date, required_year, required_week, status, note)
    VALUES (shop_ids[2], business_date, EXTRACT(ISOYEAR FROM current_monday)::INT,
        EXTRACT(WEEK FROM current_monday)::INT, 'OPEN', 'Rolling demo: split Robusta order')
    RETURNING demand_id INTO demand_id_value;
    INSERT INTO demand_items(demand_id, product_id, quantity)
    VALUES (demand_id_value, product_ids[2], 120);

    -- 4: positive saved Decaf supply, but no active offering to satisfy the line.
    INSERT INTO demands(shop_id, request_date, required_year, required_week, status, note)
    VALUES (shop_ids[1], business_date, EXTRACT(ISOYEAR FROM current_monday)::INT,
        EXTRACT(WEEK FROM current_monday)::INT, 'OPEN', 'Rolling demo: ineligible stopped offering')
    RETURNING demand_id INTO demand_id_value;
    INSERT INTO demand_items(demand_id, product_id, quantity)
    VALUES (demand_id_value, product_ids[3], 10);

    -- Next week is computed from its actual date, even across an ISO year boundary.
    delivery_date := current_monday + 7;
    INSERT INTO demands(shop_id, request_date, required_year, required_week, status, note)
    VALUES (shop_ids[1], business_date, EXTRACT(ISOYEAR FROM delivery_date)::INT,
        EXTRACT(WEEK FROM delivery_date)::INT, 'OPEN', 'Rolling demo: separate next-week decimal demand')
    RETURNING demand_id INTO demand_id_value;
    INSERT INTO demand_items(demand_id, product_id, quantity) VALUES
        (demand_id_value, product_ids[1], 10.25),
        (demand_id_value, product_ids[2], 5.50);

    -- Refuse to commit a fixture whose current week expired while it was loading.
    IF current_monday <> date_trunc('week', clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE THEN
        RAISE EXCEPTION 'Business week changed while seeding. Roll back and rerun in the empty demo database.';
    END IF;
    RAISE NOTICE 'Demo ready: current ISO week %/%, next ISO week %/%. Unit kg; prices VND/kg. No orders are precreated.',
        EXTRACT(WEEK FROM current_monday), EXTRACT(ISOYEAR FROM current_monday),
        EXTRACT(WEEK FROM delivery_date), EXTRACT(ISOYEAR FROM delivery_date);
END
$demo$;

COMMIT;

-- Use these actual IDs for the configured test accounts; sequences need not start at 1.
SELECT shop_id, name FROM coffee_shops ORDER BY shop_id;
SELECT supplier_id, name FROM suppliers ORDER BY supplier_id;
SELECT product_id, name, unit FROM coffee_products ORDER BY product_id;
SELECT required_year, required_week, count(*) AS demands
FROM demands GROUP BY required_year, required_week ORDER BY required_year, required_week;
