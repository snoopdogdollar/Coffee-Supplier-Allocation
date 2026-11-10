package com.coffeeallocation.view;

import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Presentation helpers; business validation remains in the services. */
public final class UiKit {
    public static final Color CREAM = new Color(246, 244, 239);
    public static final Color OLIVE = new Color(82, 101, 69);
    private UiKit() {}

    public static JPanel panel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        panel.setBackground(CREAM);
        return panel;
    }

    public static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 5));
        row.setOpaque(false);
        for (Component component : components) row.add(component);
        return row;
    }

    public static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> {
            try { action.run(); }
            catch (RuntimeException exception) { error(button, exception); }
        });
        return button;
    }

    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    public static boolean confirm(Component parent, String text) {
        return JOptionPane.showConfirmDialog(parent, text, "Xác nhận thao tác",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static void message(Component parent, String text) {
        if (GraphicsEnvironment.isHeadless()) return;
        JOptionPane.showMessageDialog(parent, text, "Coffee Supplier Allocation", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, Throwable exception) {
        String message;
        if (exception instanceof SQLException sql) {
            String state = sql.getSQLState();
            message = "40001".equals(state) || "40P01".equals(state)
                    ? "Dữ liệu vừa thay đổi do thao tác đồng thời. Hãy tải lại và xác nhận kết quả mới."
                    : "Không thể hoàn tất thao tác với database. Kiểm tra kết nối và cấu hình DB_URL, DB_USER, DB_PASSWORD rồi thử lại.";
        } else {
            message = exception.getMessage() == null ? "Không thể hoàn tất thao tác." : exception.getMessage();
        }
        if (parent instanceof JComponent component) component.putClientProperty("lastError", exception);
        if (GraphicsEnvironment.isHeadless()) return;
        JOptionPane.showMessageDialog(parent, message, "Không thể thực hiện", JOptionPane.ERROR_MESSAGE);
    }

    public static long positiveId(String text, String label) {
        try {
            long value = Long.parseLong(text.trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + " phải là số nguyên dương.");
    }

    public static int integer(String text, String label) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " phải là số nguyên."); }
    }

    public static BigDecimal decimal(String text, String label) {
        try { return new BigDecimal(text.trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " phải là số, dùng dấu chấm cho phần thập phân."); }
    }
}
