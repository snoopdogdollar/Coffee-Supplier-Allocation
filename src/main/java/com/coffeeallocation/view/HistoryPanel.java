package com.coffeeallocation.view;

import com.coffeeallocation.model.SupplierHistory;
import com.coffeeallocation.service.SupplierHistoryService;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Feedback aggregates, retaining the service's database-clock cutoff convention. */
public final class HistoryPanel extends JPanel {
    private final SupplierHistoryService service = new SupplierHistoryService();
    private final JTextField cutoff = new JTextField(24);
    private final JLabel clock = new JLabel("Chưa tải báo cáo.");
    private final JLabel status = new JLabel("Để trống mốc thời gian để dùng giờ hiện tại của database.");
    private final DefaultTableModel model = UiKit.model(
            "Supplier ID", "Nhà cung cấp", "Số đơn đánh giá", "Chất lượng đạt", "Đủ số lượng", "Đúng hạn", "Đánh giá gần nhất");

    public HistoryPanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        putClientProperty("statusLabel", status);
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.add(UiKit.row(new JLabel("Mốc thời gian"), cutoff,
                UiKit.button("Tải lịch sử", this::loadReport)), BorderLayout.NORTH);
        JPanel explanation = new JPanel();
        explanation.setLayout(new BoxLayout(explanation, BoxLayout.Y_AXIS));
        explanation.add(new JLabel("Nhập yyyy-MM-ddTHH:mm:ss (giờ của database), hoặc để trống để xem hiện tại."));
        explanation.add(new JLabel("Tỷ lệ phản ánh đánh giá của quán; mỗi đơn đóng góp review hợp lệ đầu tiên. N/A: chưa có dữ liệu."));
        explanation.add(clock);
        header.add(explanation, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(UiKit.table(model)), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        cutoff.addActionListener(event -> loadReport());
    }

    private void loadReport() {
        try {
            String input = cutoff.getText().strip();
            LocalDateTime requested;
            try {
                requested = input.isEmpty() ? null : LocalDateTime.parse(input.replace(' ', 'T'));
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Mốc thời gian cần có dạng 2026-11-07T14:30:00.", exception);
            }
            UiTasks.run(this, "Đang tổng hợp lịch sử…", () -> service.findReport(requested), this::display);
        } catch (RuntimeException exception) {
            UiKit.error(this, exception);
        }
    }

    private void display(SupplierHistory.Report report) {
        model.setRowCount(0);
        for (var supplier : report.suppliers()) {
            model.addRow(new Object[]{supplier.supplierId(), supplier.supplierName(), supplier.reviewCount(),
                    rate(supplier.qualityOkCount(), supplier.reviewCount()),
                    rate(supplier.quantityOkCount(), supplier.reviewCount()),
                    rate(supplier.onTimeOkCount(), supplier.reviewCount()), supplier.latestReviewAt()});
        }
        clock.setText("Dữ liệu đến " + report.cutoff().timestamp() + " · Múi giờ database: " + report.cutoff().timeZone());
        status.setText(report.suppliers().isEmpty() ? "Chưa có nhà cung cấp." : "Đã tải " + report.suppliers().size() + " nhà cung cấp.");
    }

    private static String rate(long count, long total) {
        if (total == 0) return "N/A";
        String percentage = BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).toPlainString();
        return count + "/" + total + " (" + percentage + "%)";
    }
}
