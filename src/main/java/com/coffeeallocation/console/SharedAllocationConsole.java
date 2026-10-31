// show the result of the shared allocation preview in the console. 
// This class is responsible for formatting and printing the allocation results, 
// including the proposed allocations for each demand item and the remaining supply after the preview.
package com.coffeeallocation.console;

import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.BatchItemAllocation;
import com.coffeeallocation.model.OpenDemandItem;
import com.coffeeallocation.service.SharedAllocationService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedAllocationConsole {
    private SharedAllocationConsole() {
    }

    public static void run(int year, int week) throws SQLException {
        BatchAllocationPreview preview = new SharedAllocationService().preview(year, week);
        System.out.println("=== Shared allocation preview: ISO week " + week + "/" + year + " ===");
        System.out.println("Scope: OPEN demand items without allocation history.");
        System.out.println("Priority: request date, demand ID, then item ID.");

        if (preview.getItems().isEmpty()) {
            System.out.println("No eligible demand items for this preview.");
            return;
        }

        Map<Long, OpenDemandItem> products = new LinkedHashMap<>();
        for (BatchItemAllocation result : preview.getItems()) {
            OpenDemandItem item = result.getItem();
            products.putIfAbsent(item.getProductId(), item);
            String unit = item.getUnit();
            System.out.println();
            System.out.println("Item " + item.getDemandItemId() + " | Demand " + item.getDemandId()
                    + " | " + item.getShopName() + " | " + item.getProductName());
            System.out.println("Requested: " + item.getQuantity().toPlainString() + " " + unit);
            System.out.println("Available before this item: "
                    + result.getAvailableBefore().toPlainString() + " " + unit);

            if (result.getAllocations().isEmpty()) {
                System.out.println("Insufficient supply. No allocation proposed.");
                System.out.println("Supply shortfall: "
                        + result.getSupplyShortfall().toPlainString() + " " + unit);
            } else {
                for (var entry : result.getAllocations().entrySet()) {
                    AvailableSupply supply = preview.findSupply(item.getProductId(), entry.getKey());
                    System.out.println(supply.getSupplierName() + " | Proposed: "
                            + entry.getValue().toPlainString() + " " + unit);
                }
            }
            System.out.println("Total proposed: " + result.getProposedQuantity().toPlainString()
                    + " " + unit);
            System.out.println("Unallocated in preview: "
                    + result.getUnallocatedQuantity().toPlainString() + " " + unit);
        }

        System.out.println();
        System.out.println("=== Supply remaining in this preview ===");
        for (var group : preview.getSuppliesByProduct().entrySet()) {
            OpenDemandItem product = products.get(group.getKey());
            System.out.println(product.getProductName() + ":");
            for (AvailableSupply supply : group.getValue()) {
                BigDecimal balance = preview.getRemainingBySupply().get(supply.getSupplyId());
                BigDecimal proposed = supply.getRemainingQuantity().subtract(balance);
                System.out.println(supply.getSupplierName()
                        + " | Before: " + supply.getRemainingQuantity().toPlainString()
                        + " | Proposed: " + proposed.toPlainString()
                        + " | Left: " + balance.toPlainString() + " " + product.getUnit());
            }
        }
        System.out.println("Preview only. Nothing has been reserved or saved.");
    }
}
