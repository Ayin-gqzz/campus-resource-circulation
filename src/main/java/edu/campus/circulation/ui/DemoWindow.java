package edu.campus.circulation.ui;

import edu.campus.circulation.service.DemoService;
import edu.campus.circulation.service.DemoService.Action;
import edu.campus.circulation.service.DemoService.Category;
import edu.campus.circulation.service.DemoService.Condition;
import edu.campus.circulation.service.DemoService.Draft;
import edu.campus.circulation.service.DemoService.Exchange;
import edu.campus.circulation.service.DemoService.Item;
import edu.campus.circulation.service.DemoService.Profile;
import edu.campus.circulation.service.DemoService.Status;
import edu.campus.circulation.service.DemoService.TradeType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public final class DemoWindow extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(DemoWindow.class.getName());
    private static final Color INK = new Color(0x183A30);
    private static final Color PALE = new Color(0xE4F2E9);

    private final DemoService service;
    private final JComboBox<Profile> profiles = new JComboBox<>(Profile.values());
    private final JComboBox<Category> categoryFilter = new JComboBox<>();
    private final JTextField keyword = new JTextField(18);
    private final JLabel credit = new JLabel(" ");
    private final JLabel status = new JLabel("正在读取演示数据…");
    private final JTextArea itemDetails = detailArea();
    private final JTextArea exchangeDetails = detailArea();
    private final DefaultTableModel marketModel = model("物品", "分类", "成色", "流转条件", "发布人");
    private final DefaultTableModel historyModel = model("物品", "我的角色", "对方", "状态", "我的评价");
    private final JTable marketTable = table(marketModel);
    private final JTable historyTable = table(historyModel);
    private final JButton request = new JButton("发起意向");
    private final JButton publish = new JButton("发布物品");
    private final JButton accept = new JButton("接受");
    private final JButton reject = new JButton("拒绝");
    private final JButton complete = new JButton("确认交接完成");
    private final JButton cancel = new JButton("取消意向");
    private final JButton review = new JButton("评价对方");
    private final JTabbedPane tabs = new JTabbedPane();

    private List<Category> categories = List.of();
    private List<Item> items = List.of();
    private List<Exchange> exchanges = List.of();
    private boolean busy;

    public DemoWindow(DemoService service, Path file) {
        super("校园循环 · Swing 演示");
        this.service = service;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(840, 560));
        setSize(980, 650);
        setLocationRelativeTo(null);

        profiles.setSelectedItem(Profile.CHEN);
        add(header(), BorderLayout.NORTH);
        tabs.setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));
        tabs.addTab("闲置集市", marketPanel());
        tabs.addTab("我的流转", historyPanel());
        add(tabs, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(4, 20, 12, 20));
        status.setToolTipText("演示数据：" + file);
        add(status, BorderLayout.SOUTH);
        profiles.addActionListener(event -> {
            historyTable.clearSelection();
            historyModel.setRowCount(0);
            exchanges = List.of();
            credit.setText(" ");
            refresh();
        });
        marketTable.getSelectionModel().addListSelectionListener(event -> updateSelection());
        historyTable.getSelectionModel().addListSelectionListener(event -> updateSelection());
        runTask(service::categories, values -> {
            categories = values;
            categoryFilter.addItem(new Category(0, "全部分类"));
            values.forEach(categoryFilter::addItem);
            refresh();
        });
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setBackground(PALE);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
        JLabel title = new JLabel("校园循环");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        JLabel caption = new JLabel("校园闲置流转演示 · 浏览、发布、交接和互评");
        caption.setForeground(INK);
        JPanel copy = new JPanel(new GridLayout(2, 1, 0, 2));
        copy.setOpaque(false);
        copy.add(title);
        copy.add(caption);
        panel.add(copy, BorderLayout.CENTER);
        JPanel identity = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        identity.setOpaque(false);
        identity.add(new JLabel("演示身份"));
        identity.add(profiles);
        identity.add(credit);
        panel.add(identity, BorderLayout.EAST);
        return panel;
    }

    private JPanel marketPanel() {
        JButton search = new JButton("筛选");
        search.addActionListener(event -> refresh());
        keyword.addActionListener(event -> refresh());
        publish.addActionListener(event -> publishItem());
        request.addActionListener(event -> requestItem());
        JPanel tools = bar(new JLabel("关键词"), keyword, categoryFilter, search);
        JPanel actions = bar(request, publish);
        return view(tools, marketTable, itemDetails, actions);
    }

    private JPanel historyPanel() {
        JButton reload = new JButton("刷新");
        reload.addActionListener(event -> refresh());
        accept.addActionListener(event -> transition(Action.ACCEPT));
        reject.addActionListener(event -> transition(Action.REJECT));
        complete.addActionListener(event -> transition(Action.COMPLETE));
        cancel.addActionListener(event -> transition(Action.CANCEL));
        review.addActionListener(event -> reviewPeer());
        return view(bar(reload, new JLabel("切换演示身份可查看另一方的记录")),
                historyTable, exchangeDetails, bar(accept, reject, complete, cancel, review));
    }

    private static JPanel view(JPanel tools, JTable table, JTextArea details, JPanel actions) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        panel.add(tools, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(new JScrollPane(details), BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void refresh() {
        Profile profile = profile();
        Category category = (Category) categoryFilter.getSelectedItem();
        String filter = keyword.getText();
        Item previousItem = selected(marketTable, items);
        Exchange previousExchange = selected(historyTable, exchanges);
        runTask(() -> new Snapshot(service.market(filter, category == null || category.id() == 0 ? null : category.id()),
                service.history(profile), service.credit(profile)), snapshot -> {
            marketModel.setRowCount(0);
            historyModel.setRowCount(0);
            items = snapshot.items();
            exchanges = snapshot.exchanges();
            for (Item item : items) {
                marketModel.addRow(new Object[]{item.title(), item.category(), item.condition(), terms(item),
                    Profile.nameOf(item.ownerId())});
                if (previousItem != null && previousItem.id() == item.id()) {
                    int row = marketModel.getRowCount() - 1;
                    marketTable.setRowSelectionInterval(row, row);
                }
            }
            for (Exchange exchange : exchanges) {
                boolean owner = exchange.ownerId() == profile.id();
                historyModel.addRow(new Object[]{exchange.itemTitle(), owner ? "发布人" : "请求人",
                    Profile.nameOf(owner ? exchange.requesterId() : exchange.ownerId()), exchange.status(),
                    exchange.stars() == null ? "—" : exchange.stars() + " 星"});
                if (previousExchange != null && previousExchange.id() == exchange.id()) {
                    int row = historyModel.getRowCount() - 1;
                    historyTable.setRowSelectionInterval(row, row);
                }
            }
            DemoService.Credit score = snapshot.credit();
            credit.setText(score.count() == 0 ? "暂无评分"
                    : String.format(Locale.ROOT, "信用 %.1f / 5 · %d 条", score.average(), score.count()));
            status.setText("在架物品 " + items.size() + " 件   ·   当前身份流转 " + exchanges.size() + " 条");
            updateSelection();
        });
    }

    private void updateSelection() {
        Profile profile = profile();
        Item item = selected(marketTable, items);
        Exchange exchange = selected(historyTable, exchanges);
        itemDetails.setText(item == null ? "选中一件物品，查看详情和流转条件。"
                : item.title() + " · " + terms(item) + "\n" + item.description());
        exchangeDetails.setText(exchange == null ? "选中一条记录，查看首条意向留言。"
                : exchange.itemTitle() + " · " + exchange.status() + "\n"
                    + (exchange.message() == null ? "" : exchange.message()));
        request.setEnabled(!busy && item != null && item.ownerId() != profile.id());
        publish.setEnabled(!busy && !categories.isEmpty());
        boolean owner = exchange != null && exchange.ownerId() == profile.id();
        Status state = exchange == null ? null : exchange.status();
        accept.setEnabled(!busy && owner && state == Status.PENDING);
        reject.setEnabled(accept.isEnabled());
        complete.setEnabled(!busy && owner && state == Status.ACCEPTED);
        cancel.setEnabled(!busy && (state == Status.ACCEPTED || (!owner && state == Status.PENDING)));
        review.setEnabled(!busy && state == Status.COMPLETED && exchange.stars() == null);
    }

    private void publishItem() {
        JTextField name = new JTextField(24);
        JTextArea description = new JTextArea(3, 24);
        description.setLineWrap(true);
        JComboBox<Category> category = new JComboBox<>(categories.toArray(Category[]::new));
        JComboBox<Condition> condition = new JComboBox<>(Condition.values());
        JComboBox<TradeType> type = new JComboBox<>(TradeType.values());
        JTextField terms = new JTextField(24);
        JLabel termsLabel = new JLabel("售价（元）");
        type.addActionListener(event -> termsLabel.setText(
                type.getSelectedItem() == TradeType.SALE ? "售价（元）" : "期望换物"));
        Object[] form = {"物品名称", name, "物品描述", new JScrollPane(description),
            "分类", category, "成色", condition, "流转方式", type, termsLabel, terms};
        while (confirm(form, "发布闲置物品")) {
            TradeType tradeType = (TradeType) type.getSelectedItem();
            Long cents = null;
            if (tradeType == TradeType.SALE) {
                try {
                    String amount = terms.getText().strip();
                    if (!amount.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) {
                        throw new NumberFormatException();
                    }
                    cents = new BigDecimal(amount).movePointRight(2).longValueExact();
                    if (cents <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException | ArithmeticException invalid) {
                    showError("请输入大于零的售价，最多保留两位小数。");
                    continue;
                }
            }
            if (name.getText().isBlank() || description.getText().isBlank()
                    || (tradeType == TradeType.SWAP && terms.getText().isBlank())) {
                showError("请填写物品名称、描述和流转条件。");
                continue;
            }
            Profile actor = profile();
            Draft draft = new Draft(((Category) category.getSelectedItem()).id(), name.getText(),
                    description.getText(), (Condition) condition.getSelectedItem(), tradeType, cents, terms.getText());
            runTask(() -> {
                service.publish(actor, draft);
                return null;
            }, ignored -> {
                keyword.setText("");
                categoryFilter.setSelectedIndex(0);
                refresh();
            });
            return;
        }
    }

    private void requestItem() {
        Item item = selected(marketTable, items);
        if (item == null) {
            return;
        }
        JTextArea message = new JTextArea("你好，这件物品还在吗？", 4, 28);
        message.setLineWrap(true);
        if (confirm(new Object[]{item.title(), "留言给发布人", new JScrollPane(message)}, "发起意向")) {
            Profile actor = profile();
            runTask(() -> {
                service.request(actor, item.id(), message.getText());
                return null;
            }, ignored -> {
                tabs.setSelectedIndex(1);
                refresh();
            });
        }
    }

    private void transition(Action action) {
        Exchange exchange = selected(historyTable, exchanges);
        if (exchange == null) {
            return;
        }
        if (action == Action.COMPLETE && !confirm("确认双方已在线下完成交接？", "确认交接")) {
            return;
        }
        Profile actor = profile();
        runTask(() -> {
            service.transition(actor, exchange.id(), action);
            return null;
        }, ignored -> refresh());
    }

    private void reviewPeer() {
        Exchange exchange = selected(historyTable, exchanges);
        if (exchange == null) {
            return;
        }
        JSpinner stars = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
        JTextField comment = new JTextField(26);
        if (confirm(new Object[]{"星级（1–5）", stars, "短评（可选）", comment}, "评价对方")) {
            Profile actor = profile();
            int rating = (Integer) stars.getValue();
            String text = comment.getText();
            runTask(() -> {
                service.review(actor, exchange.id(), rating, text);
                return null;
            }, ignored -> refresh());
        }
    }

    private <T> void runTask(Callable<T> task, Consumer<T> onSuccess) {
        if (busy) {
            return;
        }
        setBusy(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    showError("操作已中断，请刷新。");
                } catch (ExecutionException failure) {
                    if (failure.getCause() instanceof IllegalArgumentException validation) {
                        showError(validation.getMessage());
                    } else {
                        LOGGER.log(Level.WARNING, "演示数据操作失败", failure.getCause());
                        showError("无法读写演示数据库，请重试。");
                    }
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        profiles.setEnabled(!busy);
        keyword.setEnabled(!busy);
        categoryFilter.setEnabled(!busy);
        if (busy) {
            status.setText("正在处理…");
        }
        updateSelection();
    }

    private Profile profile() {
        return (Profile) profiles.getSelectedItem();
    }

    private boolean confirm(Object form, String title) {
        return JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void showError(String message) {
        status.setText(message);
        JOptionPane.showMessageDialog(this, message, "操作提示", JOptionPane.WARNING_MESSAGE);
    }

    private static <T> T selected(JTable table, List<T> records) {
        int row = table.getSelectedRow();
        return row < 0 || row >= records.size() ? null : records.get(row);
    }

    private static String terms(Item item) {
        return item.type() == TradeType.SALE
                ? "¥" + BigDecimal.valueOf(item.priceCents(), 2).toPlainString()
                : "换物 · " + item.swapNote();
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        return table;
    }

    private static JTextArea detailArea() {
        JTextArea area = new JTextArea(3, 30);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(PALE);
        area.setForeground(INK);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return area;
    }

    private static JPanel bar(java.awt.Component... controls) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        for (var control : controls) {
            bar.add(control);
        }
        return bar;
    }

    private record Snapshot(List<Item> items, List<Exchange> exchanges, DemoService.Credit credit) {}
}
