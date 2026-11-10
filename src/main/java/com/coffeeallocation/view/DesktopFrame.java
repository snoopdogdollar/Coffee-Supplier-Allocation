package com.coffeeallocation.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public final class DesktopFrame extends JFrame {
    public DesktopFrame() {
        super("Coffee Supplier Allocation");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        JPanel header = UiKit.panel();
        JLabel title = new JLabel("COFFEE  /  Supplier Allocation");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 22f));
        header.add(title, BorderLayout.NORTH);
        header.add(new JLabel("Nguồn cung theo tuần · Chọn Shop ID thủ công cho bản demo"), BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Nhu cầu", new DemandPanel());
        tabs.addTab("Phân bổ & nguồn cung", new AllocationPanel());
        tabs.addTab("Đơn hàng", new OrdersPanel());
        tabs.addTab("Đánh giá", new ReviewsPanel());
        tabs.addTab("Lịch sử supplier", new HistoryPanel());
        add(tabs, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (UiTasks.anyBusy()) {
                    UiKit.message(DesktopFrame.this, "Tác vụ đang chạy. Hãy chờ kết quả trước khi đóng cửa sổ.");
                    return;
                }
                dispose();
            }
        });
    }
}
