package edu.campus.circulation.ui;

import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.Inventory;
import edu.campus.circulation.domain.MeetupLocation.Campus;
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
import edu.campus.circulation.repository.JdbcNegotiationRepository.Proposal;
import edu.campus.circulation.repository.JdbcNotificationRepository.Notice;
import edu.campus.circulation.ui.DemoStyle.Button;
import edu.campus.circulation.ui.DemoStyle.Banner;
import edu.campus.circulation.ui.DemoStyle.Role;
import edu.campus.circulation.ui.DemoStyle.Surface;
import static edu.campus.circulation.ui.DemoStyle.*;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

public final class DemoWindow extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(DemoWindow.class.getName());
    private static final int HISTORY_PEER_COLUMN = 1;
    private static final DateTimeFormatter JOIN_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final DateTimeFormatter JOIN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
            .withZone(ZoneId.of("Asia/Shanghai"));
    private final DemoService service;
    private final JComboBox<Profile> profiles;
    private final JComboBox<Category> categoryFilter = new JComboBox<>();
    private final JTextField keyword = new JTextField(16) {
        @Override
        protected void paintComponent(java.awt.Graphics original) {
            super.paintComponent(original);
            if (getDocument().getLength() == 0 && !isFocusOwner()) {
                java.awt.Graphics g = original.create();
                g.setColor(MUTED);
                var metrics = g.getFontMetrics(getFont());
                g.drawString("搜索物品名称或描述", getInsets().left,
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                g.dispose();
            }
        }
    };
    private final JLabel credit = new JLabel(" ");
    private final JLabel status = new JLabel("正在读取演示数据…");
    private final JTextArea itemDetails = detailArea();
    private final JTextArea exchangeDetails = detailArea();
    private final JLabel itemTitle = label("选择一件物品", 18, true, TEXT);
    private final JLabel exchangeTitle = label("选择一条流转", 18, true, TEXT);
    private final JTextArea itemLocation = detailArea();
    private final JTextArea exchangeLocation = detailArea();
    private final JLabel marketCount = label(" ", 13, true, ON_FOREST);
    private final DefaultTableModel marketModel = model("物品", "单价 / 换物", "交易地点 / 时间");
    private final DefaultTableModel historyModel = model("物品", "对方", "状态", "交易地点 / 时间");
    private final JTable marketTable = table(marketModel);
    private final JTable historyTable = table(historyModel);
    private final Button request = new Button("发起意向", Role.SECONDARY);
    private final Button publish = new Button("发布物品", Role.SECONDARY);
    private final Button fillMarketLocation = new Button("补填交易地点", Role.SECONDARY);
    private final Button fillHistoryLocation = new Button("补填交易地点", Role.SECONDARY);
    private final Button accept = new Button("接受意向", Role.SECONDARY);
    private final Button reject = new Button("拒绝", Role.DESTRUCTIVE);
    private final Button complete = new Button("确认交接完成", Role.SECONDARY);
    private final Button cancel = new Button("取消意向", Role.DESTRUCTIVE);
    private final Button review = new Button("评价对方", Role.SECONDARY);
    private final JTextArea messageInput = new JTextArea(2, 16);
    private final Button sendMessage = new Button("发送", Role.PRIMARY);
    private final Button proposeTerms = new Button("协商条件", Role.SECONDARY);
    private final Button agreeProposal = new Button("同意新条件", Role.PRIMARY);
    private final Button declineProposal = new Button("拒绝提案", Role.SECONDARY);
    private final Button withdrawProposal = new Button("撤回提案", Role.SECONDARY);
    private Long composingExchange;
    private final CardLayout routes = new CardLayout();
    private final JPanel pages = new JPanel(routes);
    private final Button marketNav = new Button("闲置集市", Role.NAVIGATION);
    private final Button historyNav = new Button("我的流转", Role.NAVIGATION);
    private final Button profileNav = new Button("个人页面", Role.NAVIGATION);
    private final Button noticeNav = new Button("消息提醒", Role.NAVIGATION);
    private final DefaultTableModel noticeModel = model("物品", "提醒时间", "状态");
    private final JTable noticeTable = table(noticeModel);
    private final JLabel noticeTitle = label("选择一条提醒", 18, true, TEXT);
    private final JTextArea noticeState = detailArea();
    private final JTextArea noticeDetails = detailArea();
    private final Button readNotice = new Button("标为已读", Role.SECONDARY);
    private final Button viewNoticeItem = new Button("查看物品", Role.PRIMARY);
    private final Set<Long> expandedItems = new HashSet<>();
    private final Button buyTab = new Button("想买", Role.PRIMARY);
    private final Button sellTab = new Button("想卖", Role.SECONDARY);
    private ExchangeGroups.Side historySide = ExchangeGroups.Side.BUY;
    private List<ExchangeGroups.Row> historyRows = List.of();
    private List<Notice> notices = List.of();
    private final JLabel profileName = label(" ", 32, true, Color.WHITE);
    private final JLabel profileCredit = label("暂无评分", 26, true, Color.WHITE);
    private final JLabel profileReviewCount = label("共 0 条评价", 13, false, ON_FOREST);
    private final JLabel profileJoined = label(" ", 22, true, Color.WHITE);
    private final JLabel profileActivity = label(" ", 14, false, ON_FOREST);
    private final DefaultTableModel ownedModel = model("物品", "状态", "交易地点 / 时间");
    private final JTable ownedTable = table(ownedModel);
    private final JLabel marketEmpty = label("暂无符合条件的在架物品", 15, false, MUTED);
    private final JLabel historyEmpty = label("当前身份暂无流转记录", 15, false, MUTED);
    private final JLabel ownedEmpty = label("你发布的物品会出现在这里", 15, false, MUTED);

    private List<Category> categories = List.of();
    private List<Item> items = List.of();
    private List<Exchange> exchanges = List.of();
    private boolean busy;

    public static void open(DemoService service, Path file, List<Profile> profiles) {
        DemoStyle.install();
        new DemoWindow(service, file, profiles).setVisible(true);
    }

    private DemoWindow(DemoService service, Path file, List<Profile> options) {
        super("校园循环 · Swing 演示");
        this.service = service;
        profiles = new JComboBox<>(options.toArray(Profile[]::new));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        var desktop = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1260, desktop.width), Math.min(778, desktop.height));
        setLocationRelativeTo(null);

        profiles.setSelectedItem(Profile.CHEN);
        itemTitle.putClientProperty("html.disable", true);
        exchangeTitle.putClientProperty("html.disable", true);
        getContentPane().setBackground(CANVAS);
        add(sidebar(), BorderLayout.WEST);
        pages.add(marketPanel(), "market");
        pages.add(historyPanel(), "history");
        pages.add(profilePanel(), "profile");
        pages.add(notificationsPanel(), "notices");
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 28));
        workspace.add(pages, BorderLayout.CENTER);
        add(workspace, BorderLayout.CENTER);
        showPage("market", marketNav);

        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(8, 2, 14, 2));
        status.setToolTipText("演示数据：" + file);
        workspace.add(status, BorderLayout.SOUTH);
        profiles.addActionListener(event -> {
            historyTable.clearSelection();
            historyModel.setRowCount(0);
            historyRows = List.of();
            expandedItems.clear();
            exchanges = List.of();
            notices = List.of();
            noticeModel.setRowCount(0);
            noticeNav.setText("消息提醒");
            credit.setText(" ");
            profileName.setText(profile().toString());
            profileCredit.setText("正在读取…");
            profileReviewCount.setText(" ");
            profileJoined.setText(" ");
            profileJoined.setToolTipText(null);
            profileActivity.setText(" ");
            ownedModel.setRowCount(0);
            refresh();
        });
        marketTable.getSelectionModel().addListSelectionListener(event -> updateSelection());
        historyTable.getSelectionModel().addListSelectionListener(event -> updateSelection());
        noticeTable.getSelectionModel().addListSelectionListener(event -> updateNoticeSelection());
        runTask(service::categories, values -> {
            categories = values;
            categoryFilter.addItem(new Category(0, "全部分类"));
            values.forEach(categoryFilter::addItem);
            refresh();
        });
    }

    private JPanel sidebar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SIDEBAR);
        panel.setPreferredSize(new Dimension(176, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, LINE),
                BorderFactory.createEmptyBorder(28, 17, 19, 17)));
        Surface logo = new Surface(FOREST, null, 14);
        logo.setLayout(new BorderLayout());
        JLabel mark = label("循", 22, true, Color.WHITE);
        mark.setHorizontalAlignment(JLabel.CENTER);
        logo.add(mark);
        logo.setPreferredSize(new Dimension(42, 42));
        logo.setMaximumSize(logo.getPreferredSize());
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(logo);
        panel.add(Box.createVerticalStrut(12));
        panel.add(label("校园循环", 23, true, FOREST));
        panel.add(Box.createVerticalStrut(3));
        panel.add(label("华中师范大学", 12, false, MUTED));
        panel.add(Box.createVerticalStrut(30));
        marketNav.addActionListener(event -> showPage("market", marketNav));
        historyNav.addActionListener(event -> showPage("history", historyNav));
        profileNav.addActionListener(event -> showPage("profile", profileNav));
        noticeNav.addActionListener(event -> {
            showPage("notices", noticeNav);
            refresh();
        });
        for (Button navigation : new Button[]{marketNav, historyNav, profileNav, noticeNav}) {
            panel.add(navigation);
            panel.add(Box.createVerticalStrut(5));
        }
        panel.add(Box.createVerticalGlue());
        panel.add(label("演示身份", 12, true, MUTED));
        panel.add(Box.createVerticalStrut(7));
        field(profiles);
        profiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        profiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, profiles.getPreferredSize().height));
        profiles.getAccessibleContext().setAccessibleName("演示身份");
        panel.add(profiles);
        panel.add(Box.createVerticalStrut(7));
        credit.setFont(credit.getFont().deriveFont(12f));
        credit.setForeground(MUTED);
        panel.add(credit);
        return panel;
    }

    private void showPage(String name, Button selected) {
        routes.show(pages, name);
        marketNav.setSelected(marketNav == selected);
        historyNav.setSelected(historyNav == selected);
        profileNav.setSelected(profileNav == selected);
        noticeNav.setSelected(noticeNav == selected);
    }

    private JPanel marketPanel() {
        JButton search = new Button("搜索", Role.SECONDARY);
        search.addActionListener(event -> refresh());
        keyword.addActionListener(event -> refresh());
        publish.addActionListener(event -> publishItem());
        request.addActionListener(event -> requestItem());
        fillMarketLocation.addActionListener(event -> fillLocation(selected(marketTable, items).id()));
        field(keyword);
        field(categoryFilter);
        categoryFilter.setPreferredSize(new Dimension(120, categoryFilter.getPreferredSize().height));
        keyword.getAccessibleContext().setAccessibleName("搜索物品名称或描述");
        keyword.setToolTipText("输入物品名称或描述");
        categoryFilter.getAccessibleContext().setAccessibleName("物品分类");
        JPanel tools = new JPanel(new BorderLayout(8, 0));
        tools.add(keyword, BorderLayout.CENTER);
        JPanel searchControls = bar(categoryFilter, search);
        tools.add(searchControls, BorderLayout.EAST);
        return view(marketBanner(), listing("在架物品", tools, marketTable, marketEmpty),
                inspector("物品详情", itemTitle, itemLocation, itemDetails, request, fillMarketLocation));
    }

    private JPanel historyPanel() {
        JButton reload = new Button("刷新", Role.SECONDARY);
        reload.addActionListener(event -> refresh());
        accept.addActionListener(event -> transition(Action.ACCEPT));
        reject.addActionListener(event -> transition(Action.REJECT));
        complete.addActionListener(event -> transition(Action.COMPLETE));
        cancel.addActionListener(event -> transition(Action.CANCEL));
        review.addActionListener(event -> reviewPeer());
        historyTable.getAccessibleContext().setAccessibleDescription("点击物品行或按回车展开收起意向，点击子行对方姓名查看资料。");
        historyTable.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "viewPeerProfile");
        historyTable.getActionMap().put("viewPeerProfile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                var row = selectedHistoryRow();
                if (row != null && row.heading()) toggleHistoryGroup(row.itemId());
                else viewPeerProfile(selectedExchange());
            }
        });
        MouseAdapter peerLink = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                historyTable.setCursor(!busy && isPeerCell(event)
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                historyTable.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                int viewRow = historyTable.rowAtPoint(event.getPoint());
                if (busy || viewRow < 0 || event.getButton() != MouseEvent.BUTTON1 || event.getClickCount() != 1) return;
                var selectedRow = historyRows.get(historyTable.convertRowIndexToModel(viewRow));
                if (selectedRow.heading()) {
                    toggleHistoryGroup(selectedRow.itemId());
                    return;
                }
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 1 && isPeerCell(event)) {
                    viewPeerProfile(selectedRow.exchange());
                }
            }
        };
        historyTable.addMouseListener(peerLink);
        historyTable.addMouseMotionListener(peerLink);
        fillHistoryLocation.addActionListener(event -> {
            Exchange exchange = selectedExchange();
            if (exchange != null) fillLocation(exchange.itemId());
        });
        sendMessage.addActionListener(event -> sendConversationMessage());
        proposeTerms.addActionListener(event -> proposeTransactionTerms());
        agreeProposal.addActionListener(event -> respondProposal(true));
        declineProposal.addActionListener(event -> respondProposal(false));
        withdrawProposal.addActionListener(event -> withdrawCurrentProposal());
        messageInput.setLineWrap(true);
        messageInput.setWrapStyleWord(true);
        messageInput.getAccessibleContext().setAccessibleName("发送沟通留言");
        messageInput.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel detail = inspector("交接详情", exchangeTitle, exchangeLocation, exchangeDetails,
                agreeProposal, declineProposal, withdrawProposal, accept, complete, review,
                fillHistoryLocation, reject, cancel);
        JPanel discussion = new JPanel(new BorderLayout(0, 8));
        discussion.setOpaque(false);
        discussion.add(scroll(exchangeDetails), BorderLayout.CENTER);
        JPanel composer = new JPanel(new BorderLayout(0, 5));
        composer.setOpaque(false);
        JScrollPane entry = new JScrollPane(messageInput);
        entry.setBorder(BorderFactory.createLineBorder(LINE));
        composer.add(entry, BorderLayout.CENTER);
        composer.add(bar(sendMessage, proposeTerms), BorderLayout.SOUTH);
        discussion.add(composer, BorderLayout.SOUTH);
        detail.add(discussion, BorderLayout.CENTER);
        buyTab.addActionListener(event -> setHistorySide(ExchangeGroups.Side.BUY));
        sellTab.addActionListener(event -> setHistorySide(ExchangeGroups.Side.SELL));
        buyTab.setSelected(true);
        buyTab.setToolTipText("查看我提交的购买或换物意向");
        sellTab.setToolTipText("查看我发布的物品收到的意向");
        historyEmpty.setText("暂无想买的交易，去集市提交意向吧");
        return view(heading("我的流转", reload, null),
                listing("按物品收纳 · 点击展开意向", bar(buyTab, sellTab), historyTable, historyEmpty), detail);
    }

    private JPanel notificationsPanel() {
        Button reload = new Button("刷新", Role.SECONDARY);
        reload.addActionListener(event -> refresh());
        readNotice.addActionListener(event -> openNotice(false));
        viewNoticeItem.addActionListener(event -> openNotice(true));
        return view(heading("消息提醒", reload, null),
                listing("预定取消与重新上架", null, noticeTable, label("暂无提醒", 15, false, MUTED)),
                inspector("提醒详情", noticeTitle, noticeState, noticeDetails, viewNoticeItem, readNotice));
    }

    private JPanel profilePanel() {
        JPanel page = page();
        Banner overview = banner();
        JPanel info = new JPanel(new GridLayout(1, 3, 18, 0));
        info.setOpaque(false);
        info.add(metric(profileName, profileActivity));
        info.add(metric(profileCredit, profileReviewCount));
        info.add(metric(profileJoined, label("加入时间", 13, false, ON_FOREST)));
        overview.add(info, BorderLayout.CENTER);
        Button showHistory = new Button("查看我的流转", Role.SECONDARY);
        showHistory.addActionListener(event -> showPage("history", historyNav));
        JPanel top = new JPanel(new BorderLayout(0, 15));
        top.add(heading("个人页面", showHistory, null), BorderLayout.NORTH);
        top.add(overview, BorderLayout.CENTER);
        page.add(top, BorderLayout.NORTH);
        page.add(listing("我发布的物品", null, ownedTable, ownedEmpty), BorderLayout.CENTER);
        return page;
    }

    private JPanel marketBanner() {
        Banner banner = banner();
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(label("闲置集市", 34, true, Color.WHITE));
        title.add(Box.createVerticalStrut(7));
        title.add(label("南湖校区  /  桂子山校区", 14, false, ON_FOREST));
        banner.add(title, BorderLayout.CENTER);
        JPanel actions = new JPanel(new BorderLayout(0, 10));
        actions.setOpaque(false);
        actions.add(publish, BorderLayout.NORTH);
        marketCount.setHorizontalAlignment(JLabel.RIGHT);
        actions.add(marketCount, BorderLayout.SOUTH);
        banner.add(actions, BorderLayout.EAST);
        return banner;
    }

    private static Banner banner() {
        Banner banner = new Banner();
        banner.setLayout(new BorderLayout(18, 0));
        banner.setBorder(BorderFactory.createEmptyBorder(21, 25, 21, 25));
        return banner;
    }

    private static JPanel metric(JLabel main, JLabel secondary) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.add(main, BorderLayout.NORTH);
        panel.add(secondary, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel page() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 0, 0, 0));
        return panel;
    }

    private static JPanel heading(String title, JButton action, JPanel tools) {
        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.add(label(title, 30, true, TEXT), BorderLayout.WEST);
        header.add(action, BorderLayout.EAST);
        if (tools != null) {
            header.add(tools, BorderLayout.SOUTH);
        }
        return header;
    }

    private static JPanel view(JPanel header, JPanel list, JPanel details) {
        JPanel panel = page();
        panel.add(header, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.add(list, BorderLayout.CENTER);
        content.add(details, BorderLayout.EAST);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel inspector(String caption, JLabel title, JTextArea location, JTextArea details,
            JButton... buttons) {
        Surface panel = card();
        panel.setPreferredSize(new Dimension(280, 0));
        JPanel header = new JPanel(new BorderLayout(0, 7));
        header.setOpaque(false);
        header.add(label(caption, 12, true, MUTED), BorderLayout.NORTH);
        header.add(title, BorderLayout.CENTER);
        Surface meetup = new Surface(TINT, null, 16);
        meetup.setLayout(new BorderLayout(0, 3));
        meetup.setBorder(BorderFactory.createEmptyBorder(9, 11, 8, 11));
        meetup.add(label(caption.equals("交接详情") ? "当前交易条件 · 北京时间"
                : caption.equals("提醒详情") ? "物品当前状态" : "交易地点与时间 · 北京时间", 12, true, FOREST), BorderLayout.NORTH);
        location.setOpaque(false);
        location.setRows(4);
        location.setForeground(FOREST);
        meetup.add(scroll(location, TINT), BorderLayout.CENTER);
        header.add(meetup, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll(details), BorderLayout.CENTER);
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        for (JButton button : buttons) {
            // BoxLayout excludes hidden actions and lets the visible actions use the full card width.
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            actions.add(button);
        }
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private static Surface card() {
        Surface card = new Surface(SURFACE, LINE, 22);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        return card;
    }

    private static JPanel listing(String title, JPanel controls, JTable table, JLabel empty) {
        Surface card = card();
        JPanel header = new JPanel(new BorderLayout(0, 9));
        header.setOpaque(false);
        JLabel heading = label(title, 16, true, TEXT);
        heading.setBorder(BorderFactory.createEmptyBorder(1, 0, 4, 0));
        header.add(heading, BorderLayout.NORTH);
        if (controls != null) {
            controls.setOpaque(false);
            header.add(controls, BorderLayout.CENTER);
        }
        card.add(header, BorderLayout.NORTH);
        JPanel content = new JPanel(new CardLayout());
        content.setOpaque(false);
        empty.setHorizontalAlignment(JLabel.CENTER);
        content.add(scroll(table), "data");
        content.add(empty, "empty");
        card.add(content, BorderLayout.CENTER);
        Runnable update = () -> ((CardLayout) content.getLayout()).show(content,
                table.getRowCount() == 0 ? "empty" : "data");
        table.getModel().addTableModelListener(event -> update.run());
        update.run();
        return card;
    }

    private static JScrollPane scroll(Component view) {
        return scroll(view, SURFACE);
    }

    private static JScrollPane scroll(Component view, Color background) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setBackground(background);
        return pane;
    }

    private void refresh() {
        Profile profile = profile();
        Category category = (Category) categoryFilter.getSelectedItem();
        String filter = keyword.getText();
        Item previousItem = selected(marketTable, items);
        Exchange previousExchange = selectedExchange();
        var previousGroup = selectedHistoryRow();
        Notice previousNotice = selected(noticeTable, notices);
        runTask(() -> new Snapshot(service.market(filter, category == null || category.id() == 0 ? null : category.id()),
                service.history(profile), service.credit(profile), service.myItems(profile), service.joinedAt(profile),
                service.notifications(profile)), snapshot -> {
            marketModel.setRowCount(0);
            historyModel.setRowCount(0);
            items = snapshot.items();
            exchanges = snapshot.exchanges();
            for (Item item : items) {
                marketModel.addRow(new Object[]{new ListingCell(item.title(), item.category() + " · 余 " + item.inventory().available() + " 件"
                        + (item.pendingCount() == 0 ? "" : " · " + item.pendingCount() + " 人意向")),
                    terms(item), appointmentCell(item.location(), item.meetupAt())});
                if (previousItem != null && previousItem.id() == item.id()) {
                    int row = marketModel.getRowCount() - 1;
                    marketTable.setRowSelectionInterval(row, row);
                }
            }
            rebuildHistory(previousExchange == null ? null : previousExchange.id(),
                    previousGroup == null ? null : previousGroup.itemId());
            notices = snapshot.notices();
            noticeModel.setRowCount(0);
            for (Notice notice : notices) {
                noticeModel.addRow(new Object[]{new ListingCell(notice.itemTitle(), "预定取消 · 重新上架"),
                        JOIN_TIME.format(notice.createdAt()), notice.readAt() == null ? "未读" : "已读"});
                if (previousNotice != null && previousNotice.id() == notice.id()) {
                    int row = noticeModel.getRowCount() - 1;
                    noticeTable.setRowSelectionInterval(row, row);
                }
            }
            long unread = notices.stream().filter(notice -> notice.readAt() == null).count();
            noticeNav.setText(unread == 0 ? "消息提醒" : "消息提醒 (" + unread + ")");
            DemoService.Credit score = snapshot.credit();
            credit.setText(score.count() == 0 ? "暂无评分"
                    : String.format(Locale.ROOT, "信用 %.1f / 5 · %d 条", score.average(), score.count()));
            profileName.setText(profile.toString());
            profileCredit.setText(score.count() == 0 ? "暂无评分"
                    : String.format(Locale.ROOT, "%.1f / 5", score.average()));
            profileReviewCount.setText("信用评分 · " + score.count() + " 条评价");
            profileJoined.setText(JOIN_DATE.format(snapshot.joinedAt()));
            profileJoined.setToolTipText(JOIN_TIME.format(snapshot.joinedAt()) + "（北京时间）");
            profileActivity.setText("发布 " + snapshot.owned().size() + " 件物品 · " + exchanges.size() + " 条流转记录");
            marketCount.setText(items.size() + " 件在架");
            ownedModel.setRowCount(0);
            for (DemoService.OwnedItem item : snapshot.owned()) {
                ownedModel.addRow(new Object[]{new ListingCell(item.title(), inventoryText(item.inventory())),
                        item.status(), appointmentCell(item.location(), item.meetupAt())});
            }
            status.setText("集市结果 " + items.size() + " 件   ·   " + profile + "的流转 " + exchanges.size() + " 条");
            updateSelection();
            updateNoticeSelection();
        });
    }

    private ExchangeGroups.Row selectedHistoryRow() {
        return selected(historyTable, historyRows);
    }

    private Exchange selectedExchange() {
        var row = selectedHistoryRow();
        return row == null ? null : row.exchange();
    }

    private void toggleHistoryGroup(long itemId) {
        if (busy) return;
        if (!expandedItems.remove(itemId)) expandedItems.add(itemId);
        rebuildHistory(null, itemId);
    }

    private void setHistorySide(ExchangeGroups.Side side) {
        if (busy || historySide == side) return;
        historySide = side;
        buyTab.setSelected(side == ExchangeGroups.Side.BUY);
        sellTab.setSelected(side == ExchangeGroups.Side.SELL);
        buyTab.setRole(side == ExchangeGroups.Side.BUY ? Role.PRIMARY : Role.SECONDARY);
        sellTab.setRole(side == ExchangeGroups.Side.SELL ? Role.PRIMARY : Role.SECONDARY);
        historyEmpty.setText(side == ExchangeGroups.Side.BUY ? "暂无想买的交易，去集市提交意向吧"
                : "暂无收到的意向，发布的物品有人申请后会显示在这里");
        rebuildHistory(null, null);
    }

    private void rebuildHistory(Long exchangeId, Long itemId) {
        historyTable.clearSelection();
        historyRows = List.of();
        historyModel.setRowCount(0);
        historyRows = ExchangeGroups.rows(ExchangeGroups.forSide(exchanges, profile().id(), historySide), expandedItems);
        int selection = -1;
        for (int i = 0; i < historyRows.size(); i++) {
            var row = historyRows.get(i);
            Exchange exchange = row.exchange();
            if (row.heading()) {
                historyModel.addRow(new Object[]{new ListingCell((expandedItems.contains(row.itemId()) ? "▼ " : "▶ ")
                        + row.title(), row.count() + " 条意向 · " + row.pending() + " 条待处理"),
                        expandedItems.contains(row.itemId()) ? "点击收起" : "点击展开", "", "展开后查看每位同学的条件"});
                if (itemId != null && itemId == row.itemId()) selection = i;
            } else {
                boolean owner = exchange.ownerId() == profile().id();
                String peer = nameOf(owner ? exchange.requesterId() : exchange.ownerId());
                historyModel.addRow(new Object[]{new ListingCell("↳ " + peer,
                        exchange.quantity() + " 件 · " + (owner ? "对方的意向" : "我的意向") + " #" + exchange.id()),
                        new PeerCell(peer), exchange.status(), appointmentCell(exchange.location(), exchange.meetupAt())});
                if (exchangeId != null && exchangeId == exchange.id()) selection = i;
            }
        }
        if (selection >= 0) historyTable.setRowSelectionInterval(selection, selection);
        updateSelection();
    }

    private void updateNoticeSelection() {
        Notice notice = selected(noticeTable, notices);
        noticeTitle.setText(notice == null ? "选择一条提醒" : notice.itemTitle());
        noticeTitle.setToolTipText(notice == null ? null : notice.itemTitle());
        noticeState.setText(notice == null ? "选择后查看物品状态"
                : notice.available() ? "在架 · 可重新提交意向" : "暂不可申请 · 请稍后查看");
        noticeDetails.setText(notice == null ? "" : CHAT_TIME.format(notice.createdAt()) + "（北京时间）\n\n"
                + notice.body() + "\n\n这是取消时的提醒，当前状态以集市为准。");
        noticeDetails.setCaretPosition(0);
        viewNoticeItem.setEnabled(!busy && notice != null && notice.available());
        readNotice.setEnabled(!busy && notice != null && notice.readAt() == null);
    }

    private void openNotice(boolean viewItem) {
        Notice notice = selected(noticeTable, notices);
        if (notice == null || busy) return;
        Profile actor = profile();
        runTask(() -> {
            service.markNotificationRead(actor, notice.id());
            return null;
        }, ignored -> {
            if (viewItem) {
                keyword.setText(notice.itemTitle());
                categoryFilter.setSelectedIndex(0);
                showPage("market", marketNav);
            }
            refresh();
        });
    }

    private void updateSelection() {
        Profile profile = profile();
        Item item = selected(marketTable, items);
        Exchange exchange = selectedExchange();
        var group = selectedHistoryRow();
        Long selectedId = exchange == null ? null : exchange.id();
        if (!java.util.Objects.equals(composingExchange, selectedId)) {
            messageInput.setText("");
            composingExchange = selectedId;
        }
        itemTitle.setText(item == null ? "选择一件物品" : item.title());
        itemTitle.setToolTipText(item == null ? null : item.title());
        itemLocation.setText(item == null ? "选择后查看交易地点与时间"
                : locationText(item.location()) + "\n" + timeText(item.meetupAt()));
        itemDetails.setText(item == null ? "" : terms(item) + "\n" + item.condition()
                + " · " + nameOf(item.ownerId()) + "\n\n"
                + inventoryText(item.inventory()) + "\n\n"
                + item.pendingCount() + " 人已提交意向，发布人接受对应数量后才预定成功。\n\n" + item.description());
        exchangeTitle.setText(exchange == null ? "选择一条流转" : exchange.itemTitle());
        exchangeTitle.setToolTipText(exchange == null ? null : exchange.itemTitle());
        exchangeLocation.setText(exchange == null ? "选择后查看校区和交接位置"
                : exchangeTermsText(exchange));
        exchangeDetails.setText(exchange == null ? "" : exchange.status()
                + "\n发起时间：" + CHAT_TIME.format(exchange.createdAt())
                + (exchange.completedAt() == null ? "" : "\n实际完成：" + CHAT_TIME.format(exchange.completedAt()))
                + (exchange.stars() == null ? "" : " · 我的评价 " + exchange.stars() + " 星")
                + (exchange.status() == Status.PENDING ? "\n本物品共 " + exchange.pendingCount() + " 人意向，等待发布人选择。" : "")
                + "\n沟通记录 · 北京时间\n\n" + conversationText(exchange));
        if (exchange == null && group != null) {
            exchangeTitle.setText(group.title());
            exchangeTitle.setToolTipText(group.title());
            exchangeLocation.setText(group.count() + " 条意向 · " + group.pending() + " 条待处理");
            exchangeDetails.setText("点击物品行展开或收起。\n\n展开后选择具体同学的意向，可独立沟通、协商或处理预定。\n\n"
                    + "预定取消后，之前有意向的同学会收到重新上架提醒。");
        }
        itemDetails.setCaretPosition(0);
        exchangeDetails.setCaretPosition(exchangeDetails.getDocument().getLength());
        itemLocation.setCaretPosition(0);
        exchangeLocation.setCaretPosition(0);
        request.setEnabled(!busy && item != null && item.ownerId() != profile.id() && item.location() != null);
        fillMarketLocation.setEnabled(!busy && item != null && item.ownerId() == profile.id() && item.location() == null);
        publish.setEnabled(!busy && !categories.isEmpty());
        boolean owner = exchange != null && exchange.ownerId() == profile.id();
        Status state = exchange == null ? null : exchange.status();
        boolean hasLocation = exchange != null && exchange.location() != null;
        boolean hasTime = exchange != null && exchange.meetupAt() != null;
        boolean open = state == Status.PENDING || state == Status.ACCEPTED;
        Proposal proposal = exchange == null ? null : pendingProposal(exchange);
        boolean ownProposal = proposal != null && proposal.proposerId() == profile.id();
        messageInput.setEnabled(!busy && open);
        sendMessage.setEnabled(!busy && open);
        proposeTerms.setEnabled(!busy && open && proposal == null);
        agreeProposal.setVisible(open && proposal != null && !ownProposal);
        declineProposal.setVisible(agreeProposal.isVisible());
        withdrawProposal.setVisible(open && ownProposal);
        agreeProposal.setEnabled(!busy);
        declineProposal.setEnabled(!busy);
        withdrawProposal.setEnabled(!busy);
        boolean enoughStock = exchange != null && exchange.quantity() <= exchange.inventory().available();
        accept.setEnabled(!busy && owner && state == Status.PENDING && hasLocation && hasTime && enoughStock);
        accept.setToolTipText(!hasTime ? "请先通过协商条件，由双方确认交易时间"
                : enoughStock ? "仅锁定这笔申请的数量，剩余库存继续出售" : "剩余库存不足，请刷新或等待库存释放");
        reject.setEnabled(!busy && owner && state == Status.PENDING);
        complete.setEnabled(!busy && owner && state == Status.ACCEPTED && hasLocation && hasTime);
        complete.setToolTipText(hasTime ? "确认双方已在线下完成交接" : "请先通过协商条件，由双方确认交易时间");
        fillHistoryLocation.setEnabled(!busy && owner && !hasLocation
                && (state == Status.PENDING || state == Status.ACCEPTED));
        cancel.setEnabled(!busy && (state == Status.ACCEPTED || (!owner && state == Status.PENDING)));
        cancel.setText(state == Status.ACCEPTED ? "取消预定" : "撤回意向");
        review.setEnabled(!busy && state == Status.COMPLETED && exchange.stars() == null);
        request.setRole(request.isEnabled() ? Role.PRIMARY : Role.SECONDARY);
        request.setVisible(item != null && item.ownerId() != profile.id());
        fillMarketLocation.setVisible(item != null && item.location() == null && item.ownerId() == profile.id());
        fillMarketLocation.setRole(Role.PRIMARY);
        fillHistoryLocation.setVisible(owner && !hasLocation && (state == Status.PENDING || state == Status.ACCEPTED));
        fillHistoryLocation.setRole(Role.PRIMARY);
        accept.setVisible(owner && state == Status.PENDING && hasLocation);
        accept.setRole(Role.PRIMARY);
        complete.setVisible(owner && state == Status.ACCEPTED && hasLocation);
        complete.setRole(Role.PRIMARY);
        review.setVisible(state == Status.COMPLETED && exchange.stars() == null);
        review.setRole(Role.PRIMARY);
        reject.setVisible(owner && state == Status.PENDING);
        cancel.setVisible(state == Status.ACCEPTED || (!owner && state == Status.PENDING));
    }

    private boolean isPeerCell(MouseEvent event) {
        int column = historyTable.columnAtPoint(event.getPoint());
        int row = historyTable.rowAtPoint(event.getPoint());
        return row >= 0 && row < historyRows.size() && !historyRows.get(row).heading() && column >= 0
                && historyTable.convertColumnIndexToModel(column) == HISTORY_PEER_COLUMN;
    }

    private void viewPeerProfile(Exchange exchange) {
        if (exchange == null || busy) {
            return;
        }
        Profile actor = profile();
        String previousStatus = status.getText();
        runTask(() -> service.peerProfile(actor, exchange.id()), peer -> {
            String name = nameOf(peer.id());
            var score = peer.credit();
            String rating = score.count() == 0 ? "暂无评分"
                    : String.format(Locale.ROOT, "%.1f / 5（%d 条评价）", score.average(), score.count());
            status.setText(previousStatus);
            JOptionPane.showMessageDialog(this,
                    "信用评分：" + rating + "\n加入天数：" + peer.daysSinceJoining(Instant.now()) + " 天"
                            + "\n加入日期：" + JOIN_DATE.format(peer.joinedAt()) + "（北京时间）",
                    name + " · 流转资料", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private java.io.File bookImageDirectory() {
        try {
            java.io.File location = new java.io.File(DemoWindow.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            java.io.File directory = location.isFile() ? location.getParentFile() : location;
            while (directory != null) {
                java.io.File samples = new java.io.File(directory, "sample-images");
                if (samples.isDirectory()) return samples;
                directory = directory.getParentFile();
            }
        } catch (Exception ignored) {
            // Fall back to the working directory for alternative launchers.
        }
        return new java.io.File("sample-images").getAbsoluteFile();
    }

    /** Local-image demo using filename matching, not OCR or AI inference. */
    private JPanel bookImagePanel(JTextField name, JTextArea description, JComboBox<Category> category) {
        String[][] samples = {
                {"高等数学（上册）", "数学", "高等数学", "高数", "calculus"},
                {"大学英语综合教程", "英语", "大学英语", "英语", "english"},
                {"Java 程序设计", "计算机", "java"},
                {"线性代数", "数学", "线性代数", "线代"},
                {"大学物理", "物理", "大学物理", "物理", "physics"}
        };
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("图片识别"));
        JLabel preview = new JLabel("请选择本地书籍封面", JLabel.CENTER);
        JLabel result = new JLabel("识别结果：书名、科目");
        javax.swing.JButton upload = new javax.swing.JButton("上传本地图片");
        upload.setToolTipText("模拟识别：按文件名匹配高数、英语、Java、线代、物理案例");
        upload.addActionListener(event -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(bookImageDirectory());
            chooser.setDialogTitle("选择书籍封面图片");
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "图片（JPG、PNG、GIF、BMP）", "jpg", "jpeg", "png", "gif", "bmp"));
            if (chooser.showOpenDialog(panel) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            java.io.File file = chooser.getSelectedFile();
            java.awt.image.BufferedImage image;
            try {
                image = javax.imageio.ImageIO.read(file);
                if (image == null) {
                    throw new java.io.IOException("不是有效的图片");
                }
            } catch (java.io.IOException exception) {
                showError("无法读取图片，请选择有效的本地图片文件。");
                return;
            }
            double scale = Math.min(160.0 / image.getWidth(), 120.0 / image.getHeight());
            preview.setIcon(new javax.swing.ImageIcon(image.getScaledInstance(
                    Math.max(1, (int) (image.getWidth() * scale)),
                    Math.max(1, (int) (image.getHeight() * scale)), java.awt.Image.SCALE_SMOOTH)));
            preview.setText(null);
            preview.setToolTipText(file.getName());
            String filename = file.getName().toLowerCase(Locale.ROOT);
            String[] matched = null;
            for (String[] sample : samples) {
                for (int i = 2; i < sample.length; i++) {
                    if (filename.contains(sample[i])) {
                        matched = sample;
                        break;
                    }
                }
                if (matched != null) break;
            }
            if (matched == null) {
                result.setText("未匹配预设案例，原表单未更改");
                JOptionPane.showMessageDialog(panel,
                        "此功能为模拟识别，按图片文件名匹配。文件名需包含：高数、英语、Java、线代或物理。",
                        "未匹配案例", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if ((!name.getText().isBlank() || !description.getText().isBlank())
                    && JOptionPane.showConfirmDialog(panel, "用书名和科目覆盖当前名称、描述？", "填入结果",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                result.setText("已取消填入，原表单未更改");
                return;
            }
            name.setText(matched[0]);
            description.setText("科目：" + matched[1]);
            for (int i = 0; i < category.getItemCount(); i++) {
                String label = category.getItemAt(i).name();
                if (label.contains("书") || label.contains("教材")) {
                    category.setSelectedIndex(i);
                    break;
                }
            }
            result.setText("书名：" + matched[0] + " · 科目：" + matched[1]);
        });
        panel.add(upload, BorderLayout.NORTH);
        panel.add(preview, BorderLayout.CENTER);
        panel.add(result, BorderLayout.SOUTH);
        return panel;
    }

    private void publishItem() {
        JTextField name = new JTextField(24);
        JTextArea description = new JTextArea(3, 24);
        description.setLineWrap(true);
        JComboBox<Category> category = new JComboBox<>(categories.toArray(Category[]::new));
        JComboBox<Condition> condition = new JComboBox<>(Condition.values());
        JComboBox<TradeType> type = new JComboBox<>(TradeType.values());
        JTextField terms = new JTextField(24);
        QuantityField quantity = new QuantityField(9999);
        MeetupFields meetup = new MeetupFields();
        JLabel termsLabel = new JLabel("单价（元/件）");
        type.addActionListener(event -> termsLabel.setText(
                type.getSelectedItem() == TradeType.SALE ? "单价（元/件）" : "期望换物（每件）"));
        JPanel form = new JPanel(new GridLayout(1, 2, 18, 0));
        JPanel basics = new JPanel(new BorderLayout(0, 8));
        basics.add(fields("物品名称", name, "分类", category, "成色", condition, "发布数量（件）", quantity), BorderLayout.NORTH);
        JPanel descriptionField = new JPanel(new BorderLayout(0, 6));
        JLabel descriptionLabel = label("物品描述", 13, true, MUTED);
        descriptionLabel.setLabelFor(description);
        descriptionField.add(descriptionLabel, BorderLayout.NORTH);
        descriptionField.add(new JScrollPane(description), BorderLayout.CENTER);
        basics.add(descriptionField, BorderLayout.CENTER);
        basics.add(bookImagePanel(name, description, category), BorderLayout.SOUTH);
        JPanel transaction = new JPanel(new BorderLayout(0, 8));
        transaction.add(fields("流转方式", type, termsLabel, terms), BorderLayout.NORTH);
        transaction.add(meetup.panel(), BorderLayout.CENTER);
        form.add(basics);
        form.add(transaction);
        while (confirm(form, "发布闲置物品", "发布")) {
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
            if (!withinLimit(name.getText(), 80) || !withinLimit(description.getText(), 1000)
                    || (tradeType == TradeType.SWAP && !withinLimit(terms.getText(), 200))) {
                showError("物品名称最多 80 字、描述最多 1000 字、换物说明最多 200 字。");
                continue;
            }
            MeetupLocation location;
            Instant meetupAt;
            int itemQuantity;
            try {
                location = meetup.value();
                meetupAt = meetup.timeValue();
                itemQuantity = quantity.quantity();
            } catch (IllegalArgumentException invalid) {
                showError(invalid.getMessage());
                continue;
            }
            Profile actor = profile();
            Draft draft = new Draft(((Category) category.getSelectedItem()).id(), name.getText(),
                    description.getText(), (Condition) condition.getSelectedItem(), tradeType, cents, terms.getText(), location, meetupAt, itemQuantity);
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
        QuantityField quantity = new QuantityField(Math.max(1, item.inventory().available()));
        JPanel selection = fields("申请数量（剩余 " + item.inventory().available() + " 件）", quantity);
        JTextArea amount = detailArea();
        amount.setRows(2);
        amount.setText(transactionAmount(item.type() == TradeType.SALE ? item.priceCents() : null, 1));
        amount.setForeground(TEXT);
        quantity.onTextChange(value -> amount.setText(value == 0 ? "请输入有效的整数数量"
                : transactionAmount(item.type() == TradeType.SALE ? item.priceCents() : null, value)));
        while (confirm(new Object[]{item.title(), selection, amount,
                "提交意向暂不扣库存，发布人接受后才锁定对应数量。",
                "交易地点：" + locationText(item.location()), timeText(item.meetupAt()) + "（北京时间）",
                "留言给发布人", new JScrollPane(message)}, "发起意向", "发送意向")) {
            int requested;
            try {
                requested = quantity.quantity();
                if (message.getText().isBlank() || !withinLimit(message.getText(), 500)) {
                    throw new IllegalArgumentException("请输入 1–500 字的留言。");
                }
            } catch (IllegalArgumentException invalid) {
                showError(invalid.getMessage());
                continue;
            }
            Profile actor = profile();
            runTask(() -> {
                service.request(actor, item.id(), message.getText(), requested);
                return null;
            }, ignored -> {
                setHistorySide(ExchangeGroups.Side.BUY);
                showPage("history", historyNav);
                refresh();
            });
            return;
        }
    }

    private void transition(Action action) {
        Exchange exchange = selectedExchange();
        if (exchange == null) {
            return;
        }
        int remaining = exchange.inventory().available() - exchange.quantity();
        if (action == Action.ACCEPT
                && !confirm(new Object[]{"确认将「" + exchange.itemTitle() + "」预定给 "
                    + nameOf(exchange.requesterId()) + "？",
                    transactionAmount(exchange.priceCents(), exchange.quantity()),
                    "本次锁定 " + exchange.quantity() + " 件，接受后剩余 " + remaining + " 件。",
                    remaining == 0 ? "库存用尽，其余待处理意向将结束。" : "其他意向保留，可继续按余量处理。",
                    "取消预定仅释放本次锁定的数量。"}, "确认预定数量", "接受并锁定")) {
            return;
        }
        if (action == Action.COMPLETE && !confirm(new Object[]{"确认双方已在线下完成交接？",
                exchangeTermsText(exchange),
                pendingProposal(exchange) == null ? "" : "待处理的协商提案将随流转结束而失效。"},
                "确认交接", "确认完成")) {
            return;
        }
        Profile actor = profile();
        runTask(() -> {
            service.transition(actor, exchange.id(), action);
            return null;
        }, ignored -> refresh());
    }

    private void sendConversationMessage() {
        Exchange exchange = selectedExchange();
        if (exchange == null) {
            return;
        }
        String text = messageInput.getText().strip();
        if (text.isEmpty() || !withinLimit(text, 500)) {
            showError("请输入 1–500 字的沟通内容。");
            return;
        }
        Profile actor = profile();
        runTask(() -> {
            service.sendMessage(actor, exchange.id(), text);
            return null;
        }, ignored -> {
            messageInput.setText("");
            refresh();
        });
    }

    private void proposeTransactionTerms() {
        Exchange exchange = selectedExchange();
        if (exchange == null) {
            return;
        }
        JTextField price = new JTextField(20);
        boolean sale = exchange.priceCents() != null;
        price.setText(sale ? BigDecimal.valueOf(exchange.priceCents(), 2).toPlainString() : "换物");
        price.setEditable(sale);
        MeetupFields meetup = new MeetupFields();
        if (exchange.location() != null) {
            meetup.campus.setSelectedItem(exchange.location().campus());
            meetup.place.setText(exchange.location().place());
        }
        meetup.time.setInstant(exchange.meetupAt());
        Object[] form = {exchangeTermsText(exchange), "新单价（元/件，申请数量保持不变）", price, meetup.panel(),
            "提交后等待另一方同意，原条件在确认前继续有效。"};
        while (confirm(form, "协商交易条件", "提交提案")) {
            Long cents = null;
            MeetupLocation location;
            Instant meetupAt;
            try {
                if (sale) {
                    String amount = price.getText().strip();
                    if (!amount.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) {
                        throw new IllegalArgumentException("售价最多保留两位小数。");
                    }
                    cents = new BigDecimal(amount).movePointRight(2).longValueExact();
                    if (cents <= 0) {
                        throw new IllegalArgumentException("售价须大于零。");
                    }
                }
                location = meetup.value();
                meetupAt = meetup.timeValue();
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                showError(invalid.getMessage());
                continue;
            }
            Long proposedPrice = cents;
            Profile actor = profile();
            runTask(() -> {
                service.proposeTerms(actor, exchange.id(), proposedPrice, location, meetupAt);
                return null;
            }, ignored -> refresh());
            return;
        }
    }

    private void respondProposal(boolean agree) {
        Exchange exchange = selectedExchange();
        Proposal proposal = exchange == null ? null : pendingProposal(exchange);
        if (proposal == null) {
            return;
        }
        if (agree && !confirm(new Object[]{"同意后，本次流转按以下条件交接。", transactionAmount(proposal.priceCents(), exchange.quantity()),
                locationText(proposal.location()), timeText(proposal.meetupAt()) + "（北京时间）"}, "确认新交易条件", "同意并生效")) {
            return;
        }
        Profile actor = profile();
        runTask(() -> {
            service.respondToProposal(actor, exchange.id(), proposal.id(), agree);
            return null;
        }, ignored -> refresh());
    }

    private void withdrawCurrentProposal() {
        Exchange exchange = selectedExchange();
        Proposal proposal = exchange == null ? null : pendingProposal(exchange);
        if (proposal == null) {
            return;
        }
        Profile actor = profile();
        runTask(() -> {
            service.withdrawProposal(actor, exchange.id(), proposal.id());
            return null;
        }, ignored -> refresh());
    }

    private static Proposal pendingProposal(Exchange exchange) {
        return exchange.proposals().stream().filter(value -> value.status().equals("PENDING")).findFirst().orElse(null);
    }

    private static String exchangeTermsText(Exchange exchange) {
        return transactionAmount(exchange.priceCents(), exchange.quantity()) + "\n" + historyLocationText(exchange)
                + "\n" + timeText(exchange.meetupAt());
    }

    private static String termsText(Long priceCents) {
        return priceCents == null ? "换物" : "单价 ¥" + BigDecimal.valueOf(priceCents, 2).toPlainString();
    }

    static String transactionAmount(Long unitPrice, int quantity) {
        if (unitPrice == null) return "换物 · 数量 " + quantity + " 件";
        return termsText(unitPrice) + " × " + quantity + " 件\n总价 ¥"
                + BigDecimal.valueOf(unitPrice, 2).multiply(BigDecimal.valueOf(quantity)).toPlainString();
    }

    private static String inventoryText(Inventory stock) {
        return "剩余 " + stock.available() + " 件 · 已预定 " + stock.reserved() + " 件 · 已成交 " + stock.completed() + " 件";
    }

    private String conversationText(Exchange exchange) {
        var events = new java.util.ArrayList<ConversationEvent>();
        for (var message : exchange.messages()) {
            events.add(new ConversationEvent(message.sentAt(), nameOf(message.senderId()) + "  "
                    + CHAT_TIME.format(message.sentAt()) + "\n" + message.body()));
        }
        for (Proposal proposal : exchange.proposals()) {
            String conditions = transactionAmount(proposal.priceCents(), exchange.quantity()) + "\n" + locationText(proposal.location())
                    + "\n" + timeText(proposal.meetupAt());
            events.add(new ConversationEvent(proposal.createdAt(), nameOf(proposal.proposerId())
                    + " 提出修改  " + CHAT_TIME.format(proposal.createdAt()) + "\n" + conditions));
            if (proposal.resolvedAt() != null) {
                String result = switch (proposal.status()) {
                    case "AGREED" -> "另一方已同意，条件生效";
                    case "DECLINED" -> "另一方已拒绝，原条件有效";
                    case "WITHDRAWN" -> "提案人已撤回";
                    default -> "流转结束，提案已失效";
                };
                events.add(new ConversationEvent(proposal.resolvedAt(),
                        CHAT_TIME.format(proposal.resolvedAt()) + "\n" + result + "\n" + conditions));
            } else {
                events.add(new ConversationEvent(proposal.createdAt(), "等待另一方确认以上条件"));
            }
        }
        events.sort(java.util.Comparator.comparing(ConversationEvent::at));
        return events.stream().map(ConversationEvent::text).collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private record ConversationEvent(Instant at, String text) {}

    private void fillLocation(long itemId) {
        MeetupFields meetup = new MeetupFields();
        while (confirm(meetup.panel(), "补填交易地点", "保存地点")) {
            MeetupLocation location;
            Instant meetupAt;
            try {
                location = meetup.value();
                meetupAt = meetup.timeValue();
            } catch (IllegalArgumentException invalid) {
                showError(invalid.getMessage());
                continue;
            }
            Profile actor = profile();
            runTask(() -> {
                service.fillMeetupLocation(actor, itemId, location, meetupAt);
                return null;
            }, ignored -> refresh());
            return;
        }
    }

    private void reviewPeer() {
        Exchange exchange = selectedExchange();
        if (exchange == null) {
            return;
        }
        JSpinner stars = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
        JTextField comment = new JTextField(26);
        if (confirm(fields("星级（1–5）", stars, "短评（可选）", comment), "评价对方", "提交评价")) {
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
        buyTab.setEnabled(!busy);
        sellTab.setEnabled(!busy);
        profiles.setEnabled(!busy);
        keyword.setEnabled(!busy);
        categoryFilter.setEnabled(!busy);
        if (busy) {
            status.setText("正在处理…");
        }
        updateSelection();
        updateNoticeSelection();
    }

    private Profile profile() {
        return (Profile) profiles.getSelectedItem();
    }

    private String nameOf(long id) {
        for (int i = 0; i < profiles.getItemCount(); i++) {
            Profile candidate = profiles.getItemAt(i);
            if (candidate.id() == id) return candidate.label();
        }
        return "学生 " + id;
    }

    private boolean confirm(Object form, String title, String action) {
        Button back = new Button("取消", Role.SECONDARY);
        Button submit = new Button(action, Role.PRIMARY);
        JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{back, submit});
        back.addActionListener(event -> pane.setValue(back));
        submit.addActionListener(event -> pane.setValue(submit));
        var dialog = pane.createDialog(this, title);
        dialog.getRootPane().setDefaultButton(submit);
        try {
            dialog.setVisible(true);
            return pane.getValue() == submit;
        } finally {
            dialog.dispose();
        }
    }

    private void showError(String message) {
        status.setText(message);
        JOptionPane.showMessageDialog(this, message, "操作提示", JOptionPane.WARNING_MESSAGE);
    }

    private static <T> T selected(JTable table, List<T> records) {
        int row = table.getSelectedRow();
        return row < 0 || row >= records.size() ? null : records.get(row);
    }

    private static boolean withinLimit(String input, int maximum) {
        String text = input.strip();
        return text.codePointCount(0, text.length()) <= maximum;
    }

    private static String terms(Item item) {
        return item.type() == TradeType.SALE
                ? "¥" + BigDecimal.valueOf(item.priceCents(), 2).toPlainString() + "/件"
                : "换物（每件）· " + item.swapNote();
    }

    private static String locationText(MeetupLocation location) {
        return location == null ? "待发布人补填" : location.displayName();
    }

    private static String timeText(Instant meetupAt) {
        return meetupAt == null ? "交易时间：待协商" : "交易时间：" + JOIN_TIME.format(meetupAt);
    }

    private static ListingCell appointmentCell(MeetupLocation location, Instant meetupAt) {
        return new ListingCell(locationText(location), timeText(meetupAt));
    }

    private static String historyLocationText(Exchange exchange) {
        if (exchange.location() == null && exchange.status() != Status.PENDING && exchange.status() != Status.ACCEPTED) {
            return "未记录（旧记录）";
        }
        return locationText(exchange.location());
    }

    private static final class MeetupFields {
        private final JComboBox<Campus> campus = new JComboBox<>(Campus.values());
        private final JTextField place = new JTextField(24);
        private final TradeTimeField time = new TradeTimeField();
        private final JPanel panel = fields("交易校区（必选）", campus, "具体交易地点（必填）", place,
                "交易时间（北京时间，必填）", time);

        private MeetupFields() {
            campus.setSelectedIndex(-1);
            place.setToolTipText("例如图书馆入口");
        }

        private JPanel panel() {
            return panel;
        }

        private MeetupLocation value() {
            return new MeetupLocation((Campus) campus.getSelectedItem(), place.getText());
        }

        private Instant timeValue() {
            Instant value = time.instant();
            if (!value.isAfter(Instant.now())) throw new IllegalArgumentException("请选择未来的交易时间。");
            return value;
        }
    }

    private static JPanel fields(Object... pairs) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        for (int i = 0; i < pairs.length; i += 2) {
            JLabel caption = pairs[i] instanceof JLabel existing ? existing
                    : label((String) pairs[i], 13, true, MUTED);
            var input = (javax.swing.JComponent) pairs[i + 1];
            field(input);
            caption.setLabelFor(input);
            caption.setAlignmentX(Component.LEFT_ALIGNMENT);
            input.setAlignmentX(Component.LEFT_ALIGNMENT);
            input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height));
            group.add(caption);
            group.add(Box.createVerticalStrut(4));
            group.add(input);
            if (i + 2 < pairs.length) {
                group.add(Box.createVerticalStrut(8));
            }
        }
        return group;
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
        table.setRowHeight(62);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setBackground(SURFACE);
        table.setSelectionBackground(TINT);
        table.setSelectionForeground(ACCENT);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 37));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected,
                    boolean focus, int row, int column) {
                super.getTableCellRendererComponent(owner, value, selected, focus, row, column);
                setBackground(SIDEBAR);
                setForeground(MUTED);
                setFont(owner.getFont().deriveFont(Font.BOLD, 12f));
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 4));
                return this;
            }
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final JPanel stacked = new JPanel(new GridLayout(2, 1));
            private final JLabel first = label("", 14, true, TEXT);
            private final JLabel second = label("", 12, false, MUTED);
            {
                first.putClientProperty("html.disable", true);
                second.putClientProperty("html.disable", true);
                stacked.add(first);
                stacked.add(second);
            }

            @Override
            public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected,
                    boolean focus, int row, int column) {
                super.getTableCellRendererComponent(owner, value, selected, focus, row, column);
                if (value instanceof ListingCell item) {
                    first.setText(item.title());
                    second.setText(item.subtitle());
                    stacked.setBackground(selected ? TINT : row % 2 == 0 ? SURFACE : CANVAS);
                    stacked.setBorder(BorderFactory.createCompoundBorder(
                            focus ? BorderFactory.createLineBorder(ACCENT) : BorderFactory.createEmptyBorder(1, 1, 1, 1),
                            BorderFactory.createEmptyBorder(8, 9, 8, 3)));
                    stacked.setToolTipText(item.title() + " · " + item.subtitle());
                    return stacked;
                }
                putClientProperty("html.disable", true);
                boolean peerLink = value instanceof PeerCell;
                setText(value instanceof ItemStatus itemStatus ? listingStatus(itemStatus)
                        : value instanceof PeerCell peer ? peer.name() : String.valueOf(value));
                setToolTipText(peerLink ? "查看 " + getText() + " 的信用和加入天数" : getText());
                setBackground(selected ? TINT : row % 2 == 0 ? SURFACE : CANVAS);
                setForeground(selected || peerLink || value instanceof Status || value instanceof ItemStatus ? ACCENT : TEXT);
                Font font = owner.getFont().deriveFont(column == 0 ? Font.BOLD : Font.PLAIN);
                setFont(peerLink ? font.deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)) : font);
                setBorder(BorderFactory.createCompoundBorder(
                        focus ? BorderFactory.createLineBorder(new java.awt.Color(0x4085DD)) : BorderFactory.createEmptyBorder(1, 1, 1, 1),
                        BorderFactory.createEmptyBorder(0, 9, 0, 3)));
                return this;
            }
        });
        int[] widths = table.getColumnCount() == 4 ? new int[]{153, 75, 75, 205}
                : new int[]{175, 113, 210};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return table;
    }

    private record PeerCell(String name) {}

    private static String listingStatus(ItemStatus status) {
        return switch (status) {
            case AVAILABLE -> "在架";
            case RESERVED -> "已锁定";
            case COMPLETED -> "已完成";
            case WITHDRAWN -> "已下架";
        };
    }

    private static JTextArea detailArea() {
        JTextArea area = new JTextArea(3, 30);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(SURFACE);
        area.setForeground(MUTED);
        area.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return area;
    }

    private static JPanel bar(java.awt.Component... controls) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bar.setOpaque(false);
        for (var control : controls) {
            bar.add(control);
        }
        return bar;
    }

    private record Snapshot(List<Item> items, List<Exchange> exchanges, DemoService.Credit credit,
                            List<DemoService.OwnedItem> owned, Instant joinedAt, List<Notice> notices) {}

    private record ListingCell(String title, String subtitle) {
        @Override
        public String toString() {
            return title + " · " + subtitle;
        }
    }
}
