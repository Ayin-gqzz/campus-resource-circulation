package edu.campus.circulation.ui;

import static edu.campus.circulation.ui.DemoStyle.*;

import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.CampusBuilding.Kind;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class BuildingPickerDialog {
    private final JPanel content = new JPanel(new BorderLayout(14, 12));
    private final Runnable close;
    private final CampusMapPanel map;
    private final JComboBox<Campus> campuses = new JComboBox<>(Campus.values());
    private final JTextField search = new JTextField(18);
    private final JList<CampusBuilding> matches = new JList<>();
    private final JPanel suggestions = new JPanel(new BorderLayout());
    private final JScrollPane matchScroll = new JScrollPane(matches);
    private final JLabel noMatches = label("当前校区没有匹配的楼栋", 13, false, MUTED);
    private final JComboBox<BuildingChoice> buildings = new JComboBox<>();
    private final JLabel buildingName = label("尚未选择楼栋", 16, true, FOREST);
    private final JLabel buildingCode = label("点选有色建筑或选择楼栋", 13, false, MUTED);
    private final JLabel status = label("定位为本机仿真", 13, false, MUTED);
    private final JLabel coordinates = label(" ", 12, false, MUTED);
    private final JButton confirm = new Button("确认楼栋", Role.PRIMARY);
    private final JButton locate = new Button("一键定位", Role.PRIMARY);
    private final Timer locating = new Timer(800, event -> finishLocating());
    private CampusBuilding selected;
    private CampusBuilding result;

    static Optional<CampusBuilding> pick(Component parent, Campus initialCampus, CampusBuilding initialBuilding) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        Campus campus = initialBuilding == null ? initialCampus : initialBuilding.campus();
        var dialog = new JDialog(owner, "校园地图 · 选择所在楼栋", JDialog.ModalityType.APPLICATION_MODAL);
        var picker = new BuildingPickerDialog(campus == null ? Campus.GUIZISHAN : campus,
                initialBuilding, dialog::dispose);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(picker.content());
        var root = dialog.getRootPane();
        root.setDefaultButton(picker.confirm);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dialog.dispose();
            }
        });
        dialog.pack();
        dialog.setMinimumSize(new Dimension(690, 430));
        if (owner != null) {
            var screen = owner.getGraphicsConfiguration().getBounds();
            dialog.setSize(Math.min(dialog.getWidth(), screen.width - 36),
                    Math.min(dialog.getHeight(), screen.height - 90));
        }
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(picker.search::requestFocusInWindow);
        try {
            dialog.setVisible(true);
            return picker.result();
        } finally {
            picker.locating.stop();
            picker.map.clearFix();
            dialog.dispose();
        }
    }

    BuildingPickerDialog(Campus initialCampus, CampusBuilding initialBuilding, Runnable close) {
        this.close = close;
        map = new CampusMapPanel(initialCampus, this::pickOnMap);
        campuses.setSelectedItem(initialCampus);
        locating.setRepeats(false);

        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(searchBar(), BorderLayout.NORTH);
        JPanel mapArea = new JPanel(new BorderLayout());
        mapArea.setBorder(BorderFactory.createLineBorder(LINE, 1, true));
        mapArea.add(map, BorderLayout.CENTER);
        content.add(mapArea, BorderLayout.CENTER);
        content.add(details(), BorderLayout.EAST);
        content.add(footer(), BorderLayout.SOUTH);
        wireSearch();
        fillBuildingChoices(initialCampus);
        updateSelection(initialBuilding);
        campuses.addActionListener(event -> switchCampus());
        buildings.addActionListener(event -> {
            var choice = (BuildingChoice) buildings.getSelectedItem();
            if (choice != null && choice.building() != selected) {
                search.setText("");
                chooseBuilding(choice.building());
            }
        });
        locate.addActionListener(event -> startLocating());
    }

    JPanel content() {
        return content;
    }

    Optional<CampusBuilding> result() {
        return Optional.ofNullable(result);
    }

    private JComponent searchBar() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 5));
        JPanel fields = new JPanel(new BorderLayout(12, 0));
        JLabel campusLabel = label("校区", 13, true, MUTED);
        campusLabel.setLabelFor(campuses);
        field(campuses);
        JPanel campusField = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        campusField.add(campusLabel);
        campusField.add(campuses);
        fields.add(campusField, BorderLayout.WEST);

        JLabel searchLabel = label("搜索楼栋", 13, true, MUTED);
        searchLabel.setLabelFor(search);
        search.setToolTipText("可输入楼栋编号或名称，例如 D05、5栋、图书馆");
        field(search);
        JPanel searchField = new JPanel(new BorderLayout(8, 0));
        searchField.add(searchLabel, BorderLayout.WEST);
        searchField.add(search, BorderLayout.CENTER);
        fields.add(searchField, BorderLayout.CENTER);
        wrapper.add(fields, BorderLayout.NORTH);

        matches.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        matches.setFixedCellHeight(29);
        matches.setSelectionBackground(TINT);
        matches.setSelectionForeground(FOREST);
        matches.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                var building = (CampusBuilding) value;
                return super.getListCellRendererComponent(list, building.displayName(), index, selected, focus);
            }
        });
        noMatches.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        suggestions.add(matchScroll, BorderLayout.CENTER);
        suggestions.add(noMatches, BorderLayout.SOUTH);
        suggestions.setVisible(false);
        wrapper.add(suggestions, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent details() {
        JPanel pane = new JPanel(new BorderLayout(0, 12));
        pane.setPreferredSize(new Dimension(215, 0));

        JPanel selection = new Surface(SURFACE, LINE, 18);
        selection.setLayout(new BoxLayout(selection, BoxLayout.Y_AXIS));
        selection.setBorder(BorderFactory.createEmptyBorder(13, 13, 14, 13));
        selection.add(label("所在楼栋", 12, true, MUTED));
        selection.add(buildingName);
        selection.add(buildingCode);
        pane.add(selection, BorderLayout.NORTH);

        JPanel simulation = new JPanel(new BorderLayout(0, 8));
        JLabel buildingLabel = label("选择楼栋", 13, true, MUTED);
        buildingLabel.setLabelFor(buildings);
        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 5));
        controls.add(buildingLabel);
        field(buildings);
        controls.add(buildings);
        locate.setToolTipText("在所选楼栋模拟定位，未选择时随机定位");
        controls.add(locate);
        controls.add(status);
        controls.add(coordinates);
        simulation.add(controls, BorderLayout.NORTH);
        pane.add(simulation, BorderLayout.CENTER);

        JPanel legend = new JPanel(new GridLayout(2, 2, 7, 4));
        legend.add(legendItem("宿舍", Kind.DORM));
        legend.add(legendItem("教学楼", Kind.TEACHING));
        legend.add(legendItem("图书馆", Kind.LIBRARY));
        legend.add(legendItem("食堂", Kind.DINING));
        pane.add(legend, BorderLayout.SOUTH);
        return pane;
    }

    private static JComponent legendItem(String name, Kind kind) {
        JLabel label = label(name, 12, false, FOREST);
        label.setOpaque(true);
        label.setBackground(CampusMapPanel.buildingColor(kind));
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return label;
    }

    private JComponent footer() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        JPanel view = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 0));
        JButton zoomOut = new Button("−", Role.SECONDARY);
        zoomOut.setToolTipText("缩小地图");
        zoomOut.getAccessibleContext().setAccessibleName("缩小地图");
        zoomOut.addActionListener(event -> map.zoom(1 / 1.25));
        JButton zoomIn = new Button("+", Role.SECONDARY);
        zoomIn.setToolTipText("放大地图");
        zoomIn.getAccessibleContext().setAccessibleName("放大地图");
        zoomIn.addActionListener(event -> map.zoom(1.25));
        JButton reset = new Button("复位", Role.SECONDARY);
        reset.addActionListener(event -> map.resetView());
        view.add(zoomOut);
        view.add(zoomIn);
        view.add(reset);
        view.add(label("滚轮缩放，拖动平移", 12, false, MUTED));
        footer.add(view, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        JButton cancel = new Button("取消", Role.SECONDARY);
        cancel.addActionListener(event -> close.run());
        confirm.addActionListener(event -> {
            result = selected;
            close.run();
        });
        actions.add(cancel);
        actions.add(confirm);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private void wireSearch() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshSearch(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshSearch(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshSearch(); }
        });
        matches.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && matches.getSelectedValue() != null) {
                chooseBuilding(matches.getSelectedValue());
            }
        });
        matches.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = matches.locationToIndex(event.getPoint());
                if (row >= 0 && matches.getCellBounds(row, row).contains(event.getPoint())) {
                    search.setText("");
                    search.requestFocusInWindow();
                }
            }
        });
        bindSearchArrow("UP", -1);
        bindSearchArrow("DOWN", 1);
        search.addActionListener(event -> {
            if (suggestions.isVisible()) {
                if (matches.getSelectedValue() != null) {
                    search.setText("");
                }
            } else if (confirm.isEnabled()) {
                confirm.doClick();
            }
        });
    }

    private void bindSearchArrow(String key, int direction) {
        search.getInputMap().put(KeyStroke.getKeyStroke(key), key);
        search.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int size = matches.getModel().getSize();
                if (suggestions.isVisible() && size > 0) {
                    int row = Math.max(0, Math.min(size - 1, matches.getSelectedIndex() + direction));
                    matches.setSelectedIndex(row);
                    matches.ensureIndexIsVisible(row);
                }
            }
        });
    }

    private void refreshSearch() {
        String query = search.getText();
        if (query.isBlank()) {
            suggestions.setVisible(false);
        } else {
            var results = CampusMapLayout.search(currentCampus(), query);
            matches.setListData(results.toArray(CampusBuilding[]::new));
            matchScroll.setPreferredSize(new Dimension(0, Math.min(4, results.size()) * 29 + 3));
            matchScroll.setVisible(!results.isEmpty());
            noMatches.setVisible(results.isEmpty());
            suggestions.setVisible(true);
            if (!results.isEmpty()) {
                matches.setSelectedIndex(0);
                matches.ensureIndexIsVisible(0);
            }
        }
        content.revalidate();
        content.repaint();
    }

    private void switchCampus() {
        cancelSimulation();
        map.showCampus(currentCampus());
        search.setText("");
        updateSelection(null);
        fillBuildingChoices(currentCampus());
    }

    private void fillBuildingChoices(Campus campus) {
        var model = new DefaultComboBoxModel<BuildingChoice>();
        model.addElement(new BuildingChoice(null));
        for (var entry : CampusMapLayout.buildings(campus)) {
            model.addElement(new BuildingChoice(entry.building()));
        }
        buildings.setModel(model);
    }

    private Campus currentCampus() {
        return (Campus) campuses.getSelectedItem();
    }

    private void pickOnMap(CampusBuilding building) {
        search.setText("");
        chooseBuilding(building);
    }

    private void chooseBuilding(CampusBuilding building) {
        cancelSimulation();
        map.clearFix();
        updateSelection(building);
    }

    private void updateSelection(CampusBuilding building) {
        selected = building;
        buildings.setSelectedItem(new BuildingChoice(building));
        map.showSelection(building);
        buildingName.setText(building == null ? "尚未选择楼栋" : building.label());
        buildingCode.setText(building == null ? "点选有色建筑或选择楼栋" : building.campus() + " · " + building.name());
        confirm.setEnabled(building != null && !locating.isRunning());
    }

    private void startLocating() {
        search.setText("");
        map.clearFix();
        locate.setEnabled(false);
        buildings.setEnabled(false);
        confirm.setEnabled(false);
        status.setText("正在模拟 Wi-Fi / GPS 定位…");
        coordinates.setText(" ");
        locating.restart();
    }

    private void finishLocating() {
        var fix = CampusMapLayout.simulate(currentCampus(), selected);
        locate.setEnabled(true);
        buildings.setEnabled(true);
        updateSelection(fix.building());
        confirm.setEnabled(true);
        map.showFix(fix);
        status.setText("模拟定位完成 · 精度 ±" + fix.accuracy() + " m");
        coordinates.setText(String.format(Locale.ROOT, "%.5f° N  %.5f° E", fix.latitude(), fix.longitude()));
    }

    private void cancelSimulation() {
        locating.stop();
        locate.setEnabled(true);
        buildings.setEnabled(true);
        status.setText("定位为本机仿真");
        coordinates.setText(" ");
    }

    private record BuildingChoice(CampusBuilding building) {
        @Override
        public String toString() {
            return building == null ? "请选择楼栋" : building.displayName();
        }
    }
}
