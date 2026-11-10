package com.coffeeallocation.view;

import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.NewDemandItem;
import com.coffeeallocation.service.DemandService;
import com.coffeeallocation.service.DesktopCatalogService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Demand form and read-only weekly demand list. SQL remains in DAO/services. */
public final class DemandPanel extends JPanel {
    private final DesktopCatalogService catalogService = new DesktopCatalogService();
    private final DemandService demandService = new DemandService();
    private final JComboBox<Choice> shop = new JComboBox<>();
    private final JTextField requestDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField year = new JTextField(6);
    private final JTextField week = new JTextField(4);
    private final JTextField note = new JTextField(28);
    private final JPanel linesPanel = new JPanel();
    private final List<ItemRow> rows = new ArrayList<>();
    private final JLabel catalogStatus = new JLabel("Chưa tải danh mục.");
    private final JLabel activityStatus = new JLabel("Sẵn sàng.");
    private final JLabel listStatus = new JLabel("Chọn năm và tuần, sau đó tải nhu cầu OPEN.");
    private final DefaultTableModel openModel = UiKit.model(
            "Demand ID", "Dòng ID", "Quán", "Sản phẩm", "Số lượng", "Đơn vị");
    private List<CoffeeProduct> products = List.of();

    public DemandPanel() {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        LocalDate today = LocalDate.now();
        year.setText(Integer.toString(today.get(IsoFields.WEEK_BASED_YEAR)));
        week.setText(Integer.toString(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        shop.setPrototypeDisplayValue(new Choice(0, "Chọn coffee shop đang hoạt động", ""));

        JPanel header = new JPanel(new GridLayout(0, 1, 5, 5));
        header.add(row(new JLabel("Năm ISO"), year, new JLabel("Tuần ISO"), week,
                button("Tải nhu cầu OPEN", this::loadOpenItems),
                button("Tải danh mục", this::reloadCatalog)));
        header.add(catalogStatus);
        header.add(activityStatus);
        putClientProperty("statusLabel", activityStatus);
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new BorderLayout(5, 8));
        form.setBorder(BorderFactory.createTitledBorder("Tạo nhu cầu mua hàng"));
        JPanel fields = new JPanel(new GridLayout(0, 1, 5, 5));
        fields.add(row(new JLabel("Coffee shop"), shop,
                new JLabel("Ngày yêu cầu (yyyy-MM-dd)"), requestDate));
        fields.add(row(new JLabel("Ghi chú"), note));
        fields.add(new JLabel("Lượng mua: tối đa 2 chữ số thập phân; dùng dấu chấm, ví dụ 12.50."));
        form.add(fields, BorderLayout.NORTH);

        linesPanel.setLayout(new BoxLayout(linesPanel, BoxLayout.Y_AXIS));
        JScrollPane lineScroll = new JScrollPane(linesPanel);
        lineScroll.setPreferredSize(new Dimension(900, 180));
        form.add(lineScroll, BorderLayout.CENTER);
        form.add(row(button("Thêm dòng", this::addItemRow),
                button("Tạo demand", this::submit)), BorderLayout.SOUTH);

        JPanel listing = new JPanel(new BorderLayout(5, 5));
        listing.setBorder(BorderFactory.createTitledBorder("Các dòng nhu cầu OPEN theo tuần"));
        JTable table = UiKit.table(openModel);
        listing.add(new JScrollPane(table), BorderLayout.CENTER);
        listing.add(listStatus, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, form, listing);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        addItemRow();
        SwingUtilities.invokeLater(this::reloadCatalog);
    }

    private void reloadCatalog() {
        Long selectedShop = selectedId(shop);
        UiTasks.run(this, "Đang tải danh mục...", catalogService::loadCatalog, catalog -> {
            shop.removeAllItems();
            for (var value : catalog.shops()) {
                shop.addItem(new Choice(value.getShopId(), value.getName(), ""));
            }
            selectId(shop, selectedShop);
            products = catalog.products();
            for (ItemRow itemRow : rows) {
                itemRow.reloadProducts();
            }
            catalogStatus.setText(catalog.shops().size() + " quán, "
                    + catalog.products().size() + " sản phẩm đang hoạt động."
                    + (catalog.shops().isEmpty() || catalog.products().isEmpty()
                            ? " Cần có quán và sản phẩm trước khi tạo nhu cầu." : ""));
        });
    }

    private void addItemRow() {
        ItemRow itemRow = new ItemRow();
        rows.add(itemRow);
        linesPanel.add(itemRow.panel);
        linesPanel.revalidate();
        linesPanel.repaint();
    }

    private void removeItemRow(ItemRow itemRow) {
        if (rows.size() == 1) {
            UiKit.message(this, "Nhu cầu cần ít nhất một dòng sản phẩm.");
            return;
        }
        rows.remove(itemRow);
        linesPanel.remove(itemRow.panel);
        linesPanel.revalidate();
        linesPanel.repaint();
    }

    private void submit() {
        final Draft draft;
        try {
            draft = captureDraft();
        } catch (IllegalArgumentException exception) {
            UiKit.error(this, exception);
            return;
        }
        StringBuilder summary = new StringBuilder("Tạo nhu cầu cho ")
                .append(shop.getSelectedItem()).append("?\n")
                .append("Ngày yêu cầu: ").append(draft.requestDate()).append('\n')
                .append("Tuần ISO: ").append(draft.week()).append('/').append(draft.year());
        if (!draft.note().isBlank()) {
            summary.append("\nGhi chú: ").append(draft.note());
        }
        for (int index = 0; index < draft.items().size(); index++) {
            NewDemandItem item = draft.items().get(index);
            summary.append("\n• ").append(rows.get(index).product.getSelectedItem())
                    .append(": ").append(item.getQuantity().toPlainString());
            if (!item.getNote().isBlank()) {
                summary.append(" — ").append(item.getNote());
            }
        }
        if (!UiKit.confirm(this, summary.toString())) {
            return;
        }
        UiTasks.run(this, "Đang tạo nhu cầu...", () -> demandService.createOpenDemand(
                draft.shopId(), draft.requestDate(), draft.year(), draft.week(),
                draft.note(), draft.items()), demandId -> {
            // The complete panel is disabled during submission; failed writes keep the draft.
            note.setText("");
            rows.clear();
            linesPanel.removeAll();
            addItemRow();
            UiKit.message(this, "Đã tạo demand #" + demandId + " với "
                    + draft.items().size() + " dòng sản phẩm.");
            loadOpenItems();
        });
    }

    private Draft captureDraft() {
        Choice selectedShop = (Choice) shop.getSelectedItem();
        if (selectedShop == null) {
            throw new IllegalArgumentException("Chọn coffee shop đang hoạt động.");
        }
        final LocalDate requested;
        try {
            requested = LocalDate.parse(requestDate.getText().trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Ngày yêu cầu phải có dạng yyyy-MM-dd.", exception);
        }
        int[] period = period();
        String demandNote = note.getText().trim();
        if (demandNote.codePointCount(0, demandNote.length()) > 255) {
            throw new IllegalArgumentException("Ghi chú nhu cầu tối đa 255 ký tự.");
        }
        List<NewDemandItem> items = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ItemRow itemRow = rows.get(index);
            Choice selectedProduct = (Choice) itemRow.product.getSelectedItem();
            if (selectedProduct == null) {
                throw new IllegalArgumentException("Chọn sản phẩm ở dòng " + (index + 1) + ".");
            }
            BigDecimal quantity;
            try {
                quantity = new BigDecimal(itemRow.quantity.getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Số lượng dòng " + (index + 1)
                        + " phải là số, ví dụ 12.50.", exception);
            }
            items.add(new NewDemandItem(selectedProduct.id(), quantity,
                    itemRow.itemNote.getText().trim()));
        }
        return new Draft(selectedShop.id(), requested, period[0], period[1],
                demandNote, List.copyOf(items));
    }

    private int[] period() {
        final int selectedYear;
        final int selectedWeek;
        try {
            selectedYear = Integer.parseInt(year.getText().trim());
            selectedWeek = Integer.parseInt(week.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Năm và tuần ISO phải là số nguyên.", exception);
        }
        if (selectedYear < 1 || selectedYear > 9999) {
            throw new IllegalArgumentException("Năm ISO phải từ 1 đến 9999.");
        }
        int lastWeek = LocalDate.of(selectedYear, 12, 28)
                .get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (selectedWeek < 1 || selectedWeek > lastWeek) {
            throw new IllegalArgumentException("Tuần ISO không hợp lệ cho năm " + selectedYear + ".");
        }
        return new int[]{selectedYear, selectedWeek};
    }

    private void loadOpenItems() {
        final int[] selectedPeriod;
        try {
            selectedPeriod = period();
        } catch (IllegalArgumentException exception) {
            UiKit.error(this, exception);
            return;
        }
        UiTasks.run(this, "Đang tải nhu cầu OPEN...",
                () -> catalogService.findOpenItems(selectedPeriod[0], selectedPeriod[1]), items -> {
                    openModel.setRowCount(0);
                    for (var item : items) {
                        openModel.addRow(new Object[]{item.getDemandId(), item.getDemandItemId(),
                                item.getShopName(), item.getProductName(),
                                item.getQuantity().toPlainString(), item.getUnit()});
                    }
                    listStatus.setText(items.size() + " dòng OPEN · tuần ISO "
                            + selectedPeriod[1] + "/" + selectedPeriod[0]);
                });
    }

    private final class ItemRow {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        private final JComboBox<Choice> product = new JComboBox<>();
        private final JTextField quantity = new JTextField(8);
        private final JTextField itemNote = new JTextField(19);

        private ItemRow() {
            product.setPrototypeDisplayValue(new Choice(0, "Chọn sản phẩm cà phê", "kg"));
            panel.add(new JLabel("Sản phẩm"));
            panel.add(product);
            panel.add(new JLabel("Số lượng"));
            panel.add(quantity);
            panel.add(new JLabel("Ghi chú dòng"));
            panel.add(itemNote);
            panel.add(button("Xóa dòng", () -> removeItemRow(this)));
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
            reloadProducts();
        }

        private void reloadProducts() {
            Long selected = selectedId(product);
            product.removeAllItems();
            for (CoffeeProduct value : products) {
                product.addItem(new Choice(value.getProductId(), value.getName(), value.getUnit()));
            }
            selectId(product, selected);
        }
    }

    private static Long selectedId(JComboBox<Choice> combo) {
        Choice selected = (Choice) combo.getSelectedItem();
        return selected == null ? null : selected.id();
    }

    private static void selectId(JComboBox<Choice> combo, Long id) {
        if (id == null) {
            return;
        }
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).id() == id.longValue()) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        // Require another choice if a previously selected catalog entry was deactivated.
        combo.setSelectedIndex(-1);
    }

    private static JButton button(String label, Runnable action) {
        JButton result = new JButton(label);
        result.addActionListener(event -> action.run());
        return result;
    }

    private static JPanel row(Component... components) {
        JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (Component component : components) {
            result.add(component);
        }
        return result;
    }

    private record Choice(long id, String name, String unit) {
        @Override public String toString() {
            return name + " (#" + id + ")" + (unit.isBlank() ? "" : " · " + unit);
        }
    }

    private record Draft(long shopId, LocalDate requestDate, int year, int week,
            String note, List<NewDemandItem> items) {}
}
