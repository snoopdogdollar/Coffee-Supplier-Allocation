-- Self-contained smoke/constraint test. Run after 01_schema.sql in a TEST database.
-- Fixtures are rolled back. Identity sequence counters are NOT rolled back.
BEGIN;

DO $$
DECLARE
    v_shop BIGINT;
    v_supplier BIGINT;
    v_other_supplier BIGINT;
    v_product BIGINT;
    v_sp BIGINT;
    v_supply BIGINT;
    v_demand BIGINT;
    v_item BIGINT;
    v_other_item BIGINT;
    v_run BIGINT;
    v_allocation BIGINT;
    v_order BIGINT;
    v_count INT;
    v_total NUMERIC;
BEGIN
    SELECT count(*) INTO v_count FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
      AND table_name IN ('coffee_shops', 'suppliers', 'coffee_products',
        'supplier_products', 'supplier_weekly_supply', 'demands', 'demand_items',
        'recommendation_runs', 'supplier_scores', 'allocations', 'orders',
        'order_items', 'supplier_reviews');
    IF v_count <> 13 THEN RAISE EXCEPTION 'Expected 13 project tables, got %', v_count; END IF;

    INSERT INTO coffee_shops(name) VALUES ('TEST shop') RETURNING shop_id INTO v_shop;
    INSERT INTO suppliers(name) VALUES ('TEST supplier') RETURNING supplier_id INTO v_supplier;
    INSERT INTO suppliers(name) VALUES ('TEST other supplier') RETURNING supplier_id INTO v_other_supplier;
    INSERT INTO coffee_products(name, unit) VALUES ('TEST arabica', 'kg') RETURNING product_id INTO v_product;
    INSERT INTO supplier_products(supplier_id, product_id)
        VALUES (v_supplier, v_product) RETURNING supplier_product_id INTO v_sp;
    INSERT INTO supplier_weekly_supply(supplier_product_id, week, year,
        initial_quantity, remaining_quantity, unit_price)
        VALUES (v_sp, 42, 2026, 100, 100, 125000) RETURNING supply_id INTO v_supply;
    INSERT INTO demands(shop_id, required_week, required_year, status)
        VALUES (v_shop, 42, 2026, 'TEST_OPEN') RETURNING demand_id INTO v_demand;
    INSERT INTO demand_items(demand_id, product_id, quantity)
        VALUES (v_demand, v_product, 20) RETURNING demand_item_id INTO v_item;
    INSERT INTO demand_items(demand_id, product_id, quantity)
        VALUES (v_demand, v_product, 10) RETURNING demand_item_id INTO v_other_item;
    INSERT INTO recommendation_runs(demand_item_id, algorithm_version, status)
        VALUES (v_item, 'test-v1', 'TEST_READY') RETURNING run_id INTO v_run;
    INSERT INTO supplier_scores(run_id, supplier_id, price_score, reliability_score,
        other_score, final_score, rank)
        VALUES (v_run, v_supplier, 0.8, 0.9, 0.7, 0.82, 1);

    -- Candidate query must match product + ISO period + active flags.
    SELECT count(*) INTO v_count
    FROM demand_items di JOIN demands d ON d.demand_id = di.demand_id
    JOIN coffee_products p ON p.product_id = di.product_id
    JOIN supplier_products sp ON sp.product_id = di.product_id
    JOIN suppliers s ON s.supplier_id = sp.supplier_id
    JOIN supplier_weekly_supply sws ON sws.supplier_product_id = sp.supplier_product_id
      AND sws.week = d.required_week AND sws.year = d.required_year
    WHERE di.demand_item_id = v_item AND p.is_active AND sp.is_active AND s.is_active
      AND sws.remaining_quantity > 0;
    IF v_count <> 1 THEN RAISE EXCEPTION 'Candidate query returned % instead of 1', v_count; END IF;

    -- A small transaction demonstration. This is not the final confirmation API.
    PERFORM 1 FROM demand_items WHERE demand_item_id = v_item FOR UPDATE;
    PERFORM 1 FROM supplier_weekly_supply WHERE supply_id = v_supply FOR UPDATE;
    UPDATE supplier_weekly_supply SET remaining_quantity = remaining_quantity - 20
        WHERE supply_id = v_supply AND remaining_quantity >= 20;
    IF NOT FOUND THEN RAISE EXCEPTION 'Supply deduction failed'; END IF;
    INSERT INTO allocations(demand_item_id, supplier_id, run_id, supply_id,
        allocated_quantity, allocated_price, status)
        VALUES (v_item, v_supplier, v_run, v_supply, 20, 125000, 'TEST_CONFIRMED')
        RETURNING allocation_id INTO v_allocation;
    INSERT INTO orders(shop_id, supplier_id, allocation_id, status)
        VALUES (v_shop, v_supplier, v_allocation, 'TEST_COMPLETED')
        RETURNING order_id INTO v_order;
    INSERT INTO order_items(order_id, product_id, quantity, unit_price)
        VALUES (v_order, v_product, 20, 125000) RETURNING total_price INTO v_total;
    IF v_total <> 2500000 THEN RAISE EXCEPTION 'Incorrect generated total: %', v_total; END IF;
    INSERT INTO supplier_reviews(order_id, supplier_id, quality_ok, quantity_ok, on_time_ok)
        VALUES (v_order, v_supplier, TRUE, TRUE, FALSE);

    -- Each block MUST reject its invalid input with the expected SQLSTATE class.
    BEGIN
        INSERT INTO supplier_products(supplier_id, product_id) VALUES (v_supplier, v_product);
        RAISE EXCEPTION 'Duplicate supplier/product accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO supplier_weekly_supply(supplier_product_id, week, year,
            initial_quantity, remaining_quantity, unit_price)
            VALUES (v_sp, 42, 2026, 1, 1, 1);
        RAISE EXCEPTION 'Duplicate weekly supply accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        UPDATE supplier_weekly_supply SET remaining_quantity = -1 WHERE supply_id = v_supply;
        RAISE EXCEPTION 'Negative supply accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE supplier_weekly_supply SET remaining_quantity = 101 WHERE supply_id = v_supply;
        RAISE EXCEPTION 'Balance above initial supply accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE supplier_weekly_supply SET initial_quantity = 'NaN' WHERE supply_id = v_supply;
        RAISE EXCEPTION 'NaN quantity accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE supplier_weekly_supply SET year = 2025, week = 53 WHERE supply_id = v_supply;
        RAISE EXCEPTION 'Invalid ISO week 53 in 2025 accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    -- 2026 does have ISO week 53: boundary must be accepted.
    UPDATE supplier_weekly_supply SET week = 53 WHERE supply_id = v_supply;
    UPDATE supplier_weekly_supply SET week = 42 WHERE supply_id = v_supply;
    BEGIN
        UPDATE demands SET required_year = 2025, required_week = 53 WHERE demand_id = v_demand;
        RAISE EXCEPTION 'Invalid demand week accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE demand_items SET quantity = 0 WHERE demand_item_id = v_item;
        RAISE EXCEPTION 'Zero demand accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE allocations SET demand_item_id = v_other_item WHERE allocation_id = v_allocation;
        RAISE EXCEPTION 'Run/item mismatch accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE orders SET supplier_id = v_other_supplier WHERE order_id = v_order;
        RAISE EXCEPTION 'Order/allocation supplier mismatch accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        INSERT INTO supplier_reviews(order_id, supplier_id, quality_ok, quantity_ok, on_time_ok)
            VALUES (v_order, v_other_supplier, TRUE, TRUE, TRUE);
        RAISE EXCEPTION 'Review for wrong supplier accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        DELETE FROM suppliers WHERE supplier_id = v_supplier;
        RAISE EXCEPTION 'Referenced supplier deletion accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN
        UPDATE supplier_scores SET final_score = -1 WHERE run_id = v_run;
        RAISE EXCEPTION 'Negative score accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO supplier_scores(run_id, supplier_id, price_score, reliability_score,
            other_score, final_score, rank) VALUES (v_run, v_supplier, 1, 1, 1, 1, 2);
        RAISE EXCEPTION 'Duplicate run/supplier score accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        UPDATE orders SET status = '   ' WHERE order_id = v_order;
        RAISE EXCEPTION 'Blank status accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO coffee_shops(name) VALUES (NULL);
        RAISE EXCEPTION 'NULL shop name accepted';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    BEGIN
        UPDATE order_items SET quantity = 9999999999999.99, unit_price = 9999999999999.99
            WHERE order_id = v_order;
        RAISE EXCEPTION 'Overflow total accepted';
    EXCEPTION WHEN numeric_value_out_of_range THEN NULL; END;

    -- A failure later in a subtransaction must undo its earlier balance change.
    BEGIN
        UPDATE supplier_weekly_supply SET remaining_quantity = remaining_quantity - 1
            WHERE supply_id = v_supply;
        INSERT INTO order_items(order_id, product_id, quantity, unit_price)
            VALUES (v_order, v_product, -1, 1);
        RAISE EXCEPTION 'Expected order item failure';
    EXCEPTION WHEN check_violation THEN NULL; END;
    SELECT remaining_quantity INTO v_total FROM supplier_weekly_supply WHERE supply_id = v_supply;
    IF v_total <> 80 THEN RAISE EXCEPTION 'Failed operation did not roll back balance'; END IF;

    RAISE NOTICE 'PASS: 13 tables, valid flow, candidate query, 18 rejection cases, ISO boundary and rollback.';
END $$;

ROLLBACK;
