package com.coffeeallocation.view;

import com.coffeeallocation.model.OrderView;
import com.coffeeallocation.service.OrderService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Order browsing and delivery acknowledgement; all persistence stays in OrderService. */
public final class OrdersPanel extends JPanel {
    private final OrderService service = new OrderService();
    private final DefaultTableModel ordersModel = UiKit.model(
            "Mã đơn", "Ngày đặt", "Quán", "Nhà cung cấp", "Trạng thái", "Số dòng", "Tổng tiền (VND)");
    private final DefaultTableModel linesModel = UiKit.model(
            "Sản phẩm", "Số lượng", "Đơn vị", "Đơn giá (VND)", "Thành tiền (VND)");
    private final JTable ordersTable = UiKit.table(ordersModel);
    private final JTextField orderId = new JTextField(9);
    private final JTextField shopId = new JTextField(7);
    private final JLabel status = new JLabel("Bấm Tải đơn hàng để xem dữ liệu.");
    private final JLabel detail = new JLabel("Chọn một đơn để xem chi tiết.");
    private final JButton previous = UiKit.button("Trang trước", this::previousPage);
    private final JButton next = UiKit.button("Trang sau", this::nextPage);
    private final JButton complete = UiKit.button("Xác nhận đã giao", this::completeOrder);
    private final List<Long> pageCursors = new ArrayList<>();
    private List<OrderView.Summary> page = List.of();
    private int pageIndex;
    private OrderView.Details displayed;

    public OrdersPanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        putClientProperty("statusLabel", status);
        pageCursors.add(null);
        previous.setEnabled(false);
        next.setEnabled(false);
        complete.setEnabled(false);
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(UiKit.row(UiKit.button("Tải đơn hàng", () -> loadPage(null, 0)), previous, next), BorderLayout.NORTH);
        header.add(new JLabel("Danh sách chung của bản demo; dùng đúng mã quán khi xác nhận đã giao."), BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(new JScrollPane(ordersTable), BorderLayout.CENTER);
        top.add(UiKit.row(new JLabel("Mã đơn"), orderId,
                UiKit.button("Xem chi tiết", this::openOrder), new JLabel("Mã quán"), shopId, complete), BorderLayout.SOUTH);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(detail, BorderLayout.NORTH);
        bottom.add(new JScrollPane(UiKit.table(linesModel)), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(.6);
        split.setDividerLocation(260);
        split.setMinimumSize(new Dimension(650, 350));
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        ordersTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && ordersTable.getSelectedRow() >= 0) {
                int index = ordersTable.convertRowIndexToModel(ordersTable.getSelectedRow());
                orderId.setText(String.valueOf(ordersModel.getValueAt(index, 0)));
            }
        });
        ordersTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && ordersTable.isEnabled()) openOrder();
            }
        });
        orderId.addActionListener(event -> openOrder());
        orderId.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateCompletionAction(); }
            @Override public void removeUpdate(DocumentEvent event) { updateCompletionAction(); }
            @Override public void changedUpdate(DocumentEvent event) { updateCompletionAction(); }
        });
    }

    private void loadPage(Long cursor, int targetIndex) {
        UiTasks.run(this, "Đang tải đơn hàng…", () -> service.findPage(cursor), result -> {
            if (result.isEmpty() && targetIndex > pageIndex) {
                status.setText("Không còn đơn hàng cũ hơn.");
                next.setEnabled(false);
                return;
            }
            if (targetIndex == 0) {
                pageCursors.clear();
                pageCursors.add(null);
            } else if (targetIndex >= pageCursors.size()) {
                pageCursors.add(cursor);
            }
            pageIndex = targetIndex;
            page = result;
            displayPage();
            status.setText(result.isEmpty() ? "Chưa có đơn hàng." : "Trang " + (pageIndex + 1) + " · " + result.size() + " đơn hàng");
            previous.setEnabled(pageIndex > 0);
            next.setEnabled(result.size() == 20);
        });
    }

    private void displayPage() {
        ordersModel.setRowCount(0);
        for (var order : page) {
            ordersModel.addRow(new Object[]{order.orderId(), order.orderDate(), order.shopName(),
                    order.supplierName(), order.status(), order.itemCount(), order.totalPrice()});
        }
    }

    private void previousPage() {
        if (pageIndex > 0) loadPage(pageCursors.get(pageIndex - 1), pageIndex - 1);
    }

    private void nextPage() {
        if (!page.isEmpty()) loadPage(page.getLast().orderId(), pageIndex + 1);
    }

    private void openOrder() {
        try {
            long id = UiKit.positiveId(orderId.getText(), "Mã đơn");
            UiTasks.run(this, "Đang tải chi tiết đơn…", () -> service.findDetails(id), result -> {
                displayed = result.orElse(null);
                showDetails();
                status.setText(displayed == null ? "Không tìm thấy đơn #" + id + "." : "Đã tải đơn #" + id + ".");
            });
        } catch (RuntimeException exception) {
            UiKit.error(this, exception);
        }
    }

    private void showDetails() {
        linesModel.setRowCount(0);
        updateCompletionAction();
        if (displayed == null) {
            detail.setText("Không có chi tiết đơn hàng.");
            return;
        }
        var summary = displayed.summary();
        detail.setText("Đơn #" + summary.orderId() + " · " + summary.shopName() + " · "
                + summary.supplierName() + " · " + summary.status() + " · Allocation #" + summary.allocationId());
        for (var line : displayed.items()) {
            linesModel.addRow(new Object[]{line.productName(), line.quantity(), line.unit(),
                    line.unitPrice(), line.totalPrice()});
        }
    }

    private void updateCompletionAction() {
        boolean sameOrder;
        try {
            sameOrder = displayed != null && Long.parseLong(orderId.getText().strip()) == displayed.summary().orderId();
        } catch (NumberFormatException exception) {
            sameOrder = false;
        }
        complete.setEnabled(sameOrder && "PENDING".equals(displayed.summary().status()));
    }

    private void completeOrder() {
        try {
            if (displayed == null) throw new IllegalArgumentException("Hãy xem chi tiết đơn trước khi xác nhận.");
            long id = displayed.summary().orderId();
            long owner = UiKit.positiveId(shopId.getText(), "Mã quán");
            if (!UiKit.confirm(this, "Xác nhận đơn #" + id + " của quán #" + owner
                    + " đã giao xong và chuyển sang COMPLETED?")) return;
            UiTasks.run(this, "Đang xác nhận giao hàng…", () -> service.completePendingOrder(owner, id), changed -> {
                if (!changed) {
                    displayed = null;
                    showDetails();
                    status.setText("Không cập nhật: đơn không thuộc quán này hoặc không còn PENDING. Hãy tải lại chi tiết.");
                    return;
                }
                var old = displayed.summary();
                var updated = new OrderView.Summary(old.orderId(), old.orderDate(), old.shopName(),
                        old.supplierName(), old.allocationId(), "COMPLETED", old.itemCount(), old.totalPrice());
                displayed = new OrderView.Details(updated, displayed.items());
                List<OrderView.Summary> refreshed = new ArrayList<>(page);
                for (int i = 0; i < refreshed.size(); i++) {
                    if (refreshed.get(i).orderId() == id) refreshed.set(i, updated);
                }
                page = List.copyOf(refreshed);
                displayPage();
                showDetails();
                status.setText("Đã hoàn tất đơn #" + id + ". Quán có thể gửi đánh giá ở mục Đánh giá.");
                UiKit.message(this, "Đơn #" + id + " đã chuyển sang COMPLETED.");
            });
        } catch (RuntimeException exception) {
            UiKit.error(this, exception);
        }
    }
}
