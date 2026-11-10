package com.coffeeallocation.view;

import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.service.AllocationConfirmationService;
import com.coffeeallocation.service.SharedAllocationService;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Shows the exact shared-supply snapshot that the user can approve. */
public final class AllocationPanel extends JPanel {
    private final JTextField year = new JTextField(String.valueOf(LocalDate.now().get(IsoFields.WEEK_BASED_YEAR)), 5);
    private final JTextField week = new JTextField(String.valueOf(LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), 3);
    private final DefaultTableModel items = UiKit.model("Item ID", "Demand ID", "Quán", "Sản phẩm", "Đơn vị", "Cần", "Cung trước lượt", "Đề xuất", "Chưa cấp", "Thiếu cung");
    private final DefaultTableModel sources = UiKit.model("Supply ID", "Supplier", "Khả dụng ban đầu", "Đơn giá VND/đv", "Cấp dòng này", "Còn sau toàn lượt");
    private final JTable itemTable = UiKit.table(items);
    private final JLabel status = new JLabel("Chọn tuần rồi xem trước. Chưa giữ nguồn cung hoặc tạo đơn hàng.");
    private final JLabel summary = new JLabel(" ");
    private final JButton confirm = UiKit.button("Xác nhận toàn bộ đề xuất trong tuần", this::confirmPreview);
    private BatchAllocationPreview displayed;
    private int displayedYear;
    private int displayedWeek;

    public AllocationPanel() {
        super(new BorderLayout());
        JPanel content = UiKit.panel();
        add(content);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiKit.row(new JLabel("Năm ISO"), year, new JLabel("Tuần"), week,
                UiKit.button("Xem trước phân bổ", this::preview)), BorderLayout.NORTH);
        header.add(new JLabel("Ưu tiên nhu cầu cũ; giá tăng dần. Mỗi dòng đủ toàn bộ hoặc chưa phân bổ."), BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(itemTable), new JScrollPane(UiKit.table(sources)));
        split.setResizeWeight(0.6);
        content.add(split, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setOpaque(false);
        footer.add(summary, BorderLayout.NORTH);
        footer.add(UiKit.row(confirm), BorderLayout.CENTER);
        footer.add(status, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        putClientProperty("statusLabel", status);
        confirm.setEnabled(false);
        itemTable.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showSources(); });
        DocumentListener invalidate = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { invalidatePreview(); }
            public void removeUpdate(DocumentEvent event) { invalidatePreview(); }
            public void changedUpdate(DocumentEvent event) { invalidatePreview(); }
        };
        year.getDocument().addDocumentListener(invalidate);
        week.getDocument().addDocumentListener(invalidate);
    }

    private void invalidatePreview() {
        displayed = null;
        confirm.setEnabled(false);
        items.setRowCount(0);
        sources.setRowCount(0);
        summary.setText(" ");
        status.setText("Cần xem trước cho tuần đang chọn trước khi xác nhận.");
    }

    private void preview() {
        int requestedYear = UiKit.integer(year.getText(), "Năm ISO");
        int requestedWeek = UiKit.integer(week.getText(), "Tuần ISO");
        invalidatePreview();
        UiTasks.run(this, "Đang đọc nhu cầu và nguồn cung dùng chung…",
                () -> new SharedAllocationService().preview(requestedYear, requestedWeek), result -> {
                    displayed = result;
                    displayedYear = requestedYear;
                    displayedWeek = requestedWeek;
                    BigDecimal total = BigDecimal.ZERO;
                    int count = 0;
                    int orderCount = 0;
                    for (var row : result.getItems()) {
                        var item = row.getItem();
                        items.addRow(new Object[]{item.getDemandItemId(), item.getDemandId(), item.getShopName(),
                                item.getProductName(), item.getUnit(), item.getQuantity(), row.getAvailableBefore(),
                                row.getProposedQuantity(), row.getUnallocatedQuantity(), row.getSupplyShortfall()});
                        if (!row.getAllocations().isEmpty()) count++;
                        for (var entry : row.getAllocations().entrySet()) {
                            total = total.add(entry.getValue().multiply(result.findSupply(item.getProductId(), entry.getKey())
                                    .getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
                            orderCount++;
                        }
                    }
                    summary.setText("Tuần " + requestedWeek + "/" + requestedYear + " · " + count
                            + " dòng đủ cung · " + orderCount + " đơn dự kiến · " + total.toPlainString() + " VND");
                    status.setText(result.getItems().isEmpty() ? "Không có dòng OPEN chưa phân bổ trong tuần này."
                            : "Chọn một dòng để xem nguồn cung. Preview chưa giữ nguồn cung; dữ liệu sẽ được kiểm tra lại khi xác nhận.");
                    confirm.setEnabled(count > 0);
                    if (items.getRowCount() > 0) itemTable.setRowSelectionInterval(0, 0);
                });
    }

    private void showSources() {
        sources.setRowCount(0);
        int selected = itemTable.getSelectedRow();
        if (displayed == null || selected < 0) return;
        var row = displayed.getItems().get(itemTable.convertRowIndexToModel(selected));
        long productId = row.getItem().getProductId();
        for (var supply : displayed.getSuppliesByProduct().getOrDefault(productId, java.util.List.of())) {
            sources.addRow(new Object[]{supply.getSupplyId(), supply.getSupplierName(), supply.getRemainingQuantity(),
                    supply.getUnitPrice(), row.getAllocations().getOrDefault(supply.getSupplyId(), BigDecimal.ZERO),
                    displayed.getRemainingBySupply().get(supply.getSupplyId())});
        }
    }

    private void confirmPreview() {
        if (displayed == null || UiTasks.isBusy(this)) return;
        BatchAllocationPreview approved = displayed;
        int approvedYear = displayedYear;
        int approvedWeek = displayedWeek;
        if (!UiKit.confirm(this, summary.getText() + "\n\nXác nhận TẤT CẢ dòng đủ cung hiển thị trong tuần này.\n"
                + "Hệ thống sẽ trừ nguồn cung và tạo đơn PENDING. Dòng thiếu cung vẫn chưa phân bổ.\nTiếp tục?")) return;
        // Require a fresh preview after every attempt, including a conflict or ambiguous connection failure.
        displayed = null;
        confirm.setEnabled(false);
        UiTasks.run(this, "Đang kiểm tra lại và tạo đơn hàng…",
                () -> new AllocationConfirmationService().confirm(approvedYear, approvedWeek, approved), result -> {
                    items.setRowCount(0);
                    sources.setRowCount(0);
                    summary.setText("Đã xác nhận " + result.allocatedItems() + " dòng; " + result.unallocatedItems() + " dòng chưa phân bổ.");
                    status.setText("Đã tạo đơn: " + result.orderIds() + ". Mở Đơn hàng để tải kết quả.");
                    UiKit.message(this, "Đã tạo đơn PENDING: " + result.orderIds() + "\nDemand đã ALLOCATED: " + result.allocatedDemandIds());
                }, error -> {
                    status.setText("Chưa có kết quả xác nhận. Tải Đơn hàng để kiểm tra, rồi xem preview mới trước khi thử lại.");
                    UiKit.error(this, error);
                });
    }
}
