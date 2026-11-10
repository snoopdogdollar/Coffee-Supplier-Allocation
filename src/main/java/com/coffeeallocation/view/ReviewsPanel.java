package com.coffeeallocation.view;

import com.coffeeallocation.service.SupplierReviewService;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Explicit three-criterion shop feedback, using the existing one-review-per-order service. */
public final class ReviewsPanel extends JPanel {
    private final SupplierReviewService service = new SupplierReviewService();
    private final JTextField shopId = new JTextField(9);
    private final JTextField orderId = new JTextField(9);
    private final JComboBox<String> quality = answerBox();
    private final JComboBox<String> quantity = answerBox();
    private final JComboBox<String> onTime = answerBox();
    private final JTextArea comment = new JTextArea(3, 36);
    private final JButton submit = UiKit.button("Gửi đánh giá", this::submitReview);
    private final JLabel status = new JLabel("Chỉ đánh giá đơn COMPLETED thuộc quán đã chọn; mỗi đơn gửi một lần.");
    private final DefaultTableModel reviews = UiKit.model(
            "Mã review", "Supplier ID", "Chất lượng đạt", "Đủ số lượng", "Đúng hạn", "Nhận xét", "Ngày tạo");
    private long reviewedShop = -1;
    private long reviewedOrder = -1;

    public ReviewsPanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        putClientProperty("statusLabel", status);
        comment.setLineWrap(true);
        comment.setWrapStyleWord(true);
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        addField(fields, c, 0, "Mã quán", shopId);
        addField(fields, c, 1, "Mã đơn đã hoàn tất", orderId);
        addField(fields, c, 2, "Chất lượng đạt yêu cầu?", quality);
        addField(fields, c, 3, "Nhận đủ số lượng?", quantity);
        addField(fields, c, 4, "Giao đúng hạn?", onTime);
        addField(fields, c, 5, "Nhận xét (tối đa 255 ký tự)", new JScrollPane(comment));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(new JLabel("Quán được chọn bằng mã trong bản demo. Nhà cung cấp được lấy từ đơn hàng."), BorderLayout.NORTH);
        top.add(fields, BorderLayout.CENTER);
        top.add(UiKit.row(UiKit.button("Xem đánh giá của đơn", this::loadReviews), submit), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(UiKit.table(reviews)), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        DocumentListener idsChanged = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateOrderContext(); }
            @Override public void removeUpdate(DocumentEvent event) { updateOrderContext(); }
            @Override public void changedUpdate(DocumentEvent event) { updateOrderContext(); }
        };
        shopId.getDocument().addDocumentListener(idsChanged);
        orderId.getDocument().addDocumentListener(idsChanged);
    }

    private static JComboBox<String> answerBox() {
        return new JComboBox<>(new String[]{"— Chọn câu trả lời —", "Có", "Không"});
    }

    private static void addField(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private void updateOrderContext() {
        reviews.setRowCount(0);
        quality.setSelectedIndex(0);
        quantity.setSelectedIndex(0);
        onTime.setSelectedIndex(0);
        comment.setText("");
        submit.setEnabled(!isReviewedContext());
        status.setText("Bấm Xem đánh giá của đơn để tải dữ liệu cho mã vừa nhập.");
    }

    private boolean isReviewedContext() {
        try {
            return Long.parseLong(shopId.getText().strip()) == reviewedShop
                    && Long.parseLong(orderId.getText().strip()) == reviewedOrder;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void loadReviews() {
        try {
            long owner = UiKit.positiveId(shopId.getText(), "Mã quán");
            long id = UiKit.positiveId(orderId.getText(), "Mã đơn");
            UiTasks.run(this, "Đang tải đánh giá…", () -> service.findByOrder(owner, id), result -> {
                reviews.setRowCount(0);
                for (var review : result) {
                    reviews.addRow(new Object[]{review.reviewId(), review.supplierId(), yesNo(review.qualityOk()),
                            yesNo(review.quantityOk()), yesNo(review.onTimeOk()), review.comment(), review.createdAt()});
                }
                if (!result.isEmpty()) {
                    reviewedShop = owner;
                    reviewedOrder = id;
                }
                submit.setEnabled(!isReviewedContext());
                status.setText(result.isEmpty()
                        ? "Không có đánh giá hiển thị cho cặp quán–đơn này. Khi gửi, hệ thống kiểm tra quyền và trạng thái đơn."
                        : "Đơn #" + id + " đã có đánh giá; không thể gửi thêm.");
            });
        } catch (RuntimeException exception) {
            UiKit.error(this, exception);
        }
    }

    private void submitReview() {
        try {
            long owner = UiKit.positiveId(shopId.getText(), "Mã quán");
            long id = UiKit.positiveId(orderId.getText(), "Mã đơn");
            boolean qualityOk = answer(quality, "chất lượng");
            boolean quantityOk = answer(quantity, "số lượng");
            boolean onTimeOk = answer(onTime, "thời hạn giao");
            String text = comment.getText().strip();
            if (text.codePointCount(0, text.length()) > 255) {
                throw new IllegalArgumentException("Nhận xét chỉ được chứa tối đa 255 ký tự.");
            }
            if (!UiKit.confirm(this, "Gửi đánh giá một lần cho đơn #" + id + " của quán #" + owner + "?\n"
                    + "Chất lượng: " + yesNo(qualityOk) + " · Số lượng: " + yesNo(quantityOk)
                    + " · Đúng hạn: " + yesNo(onTimeOk))) return;
            UiTasks.run(this, "Đang lưu đánh giá…",
                    () -> service.submit(owner, id, qualityOk, quantityOk, onTimeOk, text), reviewId -> {
                        reviewedShop = owner;
                        reviewedOrder = id;
                        submit.setEnabled(false);
                        quality.setSelectedIndex(0);
                        quantity.setSelectedIndex(0);
                        onTime.setSelectedIndex(0);
                        comment.setText("");
                        reviews.setRowCount(0);
                        status.setText("Đã lưu đánh giá #" + reviewId + " cho đơn #" + id
                                + ". Bấm Xem đánh giá của đơn để xem bản đã lưu.");
                        UiKit.message(this, "Đã lưu đánh giá #" + reviewId + ".");
                    });
        } catch (RuntimeException exception) {
            UiKit.error(this, exception);
        }
    }

    private static boolean answer(JComboBox<String> field, String criterion) {
        if (field.getSelectedIndex() < 1) {
            throw new IllegalArgumentException("Hãy chọn câu trả lời cho tiêu chí " + criterion + ".");
        }
        return field.getSelectedIndex() == 1;
    }

    private static String yesNo(boolean value) {
        return value ? "Có" : "Không";
    }
}
