package edu.campus.circulation.ui;

import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.CampusBuilding.Kind;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.ui.CampusMapLayout.Fix;
import edu.campus.circulation.ui.CampusMapLayout.Footprint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

final class CampusMapPanel extends JPanel {
    private static final Color LAND = new Color(0xF0F3EC);
    private static final Color CAMPUS = new Color(0xE3EDDF);
    private static final Color GREEN = new Color(0xD4E7CB);
    private static final Color ROAD_EDGE = new Color(0xCDD8C9);
    private static final Color ROAD = new Color(0xFBFAEF);
    private static final Color INK = new Color(0x25463E);
    private static final Color BLUE = new Color(0x1669CF);
    private static final Set<String> LABELED_STREETS = Set.of("珞喻路", "雄楚大道", "桂中路", "桂元路", "桂湖路", "浅水湾路");
    private static final int PICK_RADIUS_PIXELS = 8;
    private static final long PULSE_DURATION_MS = 2600;

    private final Timer pulse = new Timer(30, event -> {
        if (pulseAge() >= PULSE_DURATION_MS) {
            ((Timer) event.getSource()).stop();
        }
        repaint();
    });
    private Campus campus;
    private CampusBuilding selected;
    private CampusBuilding hovered;
    private Fix fix;
    private long pulseStarted;
    private double zoom = 1;
    private double panX;
    private double panY;

    CampusMapPanel(Campus campus, Consumer<CampusBuilding> onPick) {
        this.campus = campus;
        setPreferredSize(new Dimension(680, 472));
        setBackground(LAND);
        getAccessibleContext().setAccessibleName("校园楼栋地图");
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            private Point previous;

            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    previous = event.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (previous == null) {
                    return;
                }
                panX += event.getX() - previous.x;
                panY += event.getY() - previous.y;
                previous = event.getPoint();
                hovered = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                constrainPan();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                previous = null;
                mouseMoved(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    CampusBuilding building = buildingAt(event);
                    if (building != null) {
                        onPick.accept(building);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                hovered = buildingAt(event);
                setCursor(Cursor.getPredefinedCursor(hovered == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = null;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoomAt(Math.pow(1.15, -event.getPreciseWheelRotation()), event.getX(), event.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        var building = buildingAt(event);
        return building == null ? null : building.displayName();
    }

    private CampusBuilding buildingAt(MouseEvent event) {
        Point2D point = worldPoint(event.getX(), event.getY());
        CampusBuilding nearest = CampusMapLayout.nearest(campus, point);
        double radius = PICK_RADIUS_PIXELS / scale();
        return CampusMapLayout.footprint(nearest).distanceSquared(point) <= radius * radius ? nearest : null;
    }

    void showCampus(Campus campus) {
        this.campus = campus;
        selected = null;
        hovered = null;
        clearFix();
        resetView();
    }

    void showSelection(CampusBuilding building) {
        selected = building;
        if (building != null) {
            var anchor = CampusMapLayout.footprint(building).anchor();
            double x = screenX(anchor.x);
            double y = screenY(anchor.y);
            if (x < 36 || x > getWidth() - 36 || y < 36 || y > getHeight() - 36) {
                panX += getWidth() / 2.0 - x;
                panY += getHeight() / 2.0 - y;
                constrainPan();
            }
        }
        repaint();
    }

    void showFix(Fix fix) {
        this.fix = fix;
        pulseStarted = System.nanoTime();
        pulse.start();
        showSelection(fix.building());
    }

    void clearFix() {
        pulse.stop();
        fix = null;
        repaint();
    }

    void zoom(double multiplier) {
        zoomAt(multiplier, getWidth() / 2.0, getHeight() / 2.0);
    }

    void resetView() {
        zoom = 1;
        panX = 0;
        panY = 0;
        repaint();
    }

    @Override
    public void removeNotify() {
        pulse.stop();
        super.removeNotify();
    }

    private void zoomAt(double multiplier, double x, double y) {
        Point2D anchor = worldPoint(x, y);
        zoom = Math.max(1, Math.min(3, zoom * multiplier));
        panX += x - screenX(anchor.getX());
        panY += y - screenY(anchor.getY());
        constrainPan();
        repaint();
    }

    private void constrainPan() {
        double limitX = Math.max(0, (CampusMapLayout.WIDTH * scale() - getWidth()) / 2);
        double limitY = Math.max(0, (CampusMapLayout.HEIGHT * scale() - getHeight()) / 2);
        panX = Math.max(-limitX, Math.min(limitX, panX));
        panY = Math.max(-limitY, Math.min(limitY, panY));
    }

    private double scale() {
        double horizontal = getWidth() / (double) CampusMapLayout.WIDTH;
        double vertical = getHeight() / (double) CampusMapLayout.HEIGHT;
        return Math.max(0.001, Math.min(horizontal, vertical) * zoom);
    }

    private double screenX(double x) {
        return getWidth() / 2.0 + panX + (x - CampusMapLayout.WIDTH / 2.0) * scale();
    }

    private double screenY(double y) {
        return getHeight() / 2.0 + panY + (y - CampusMapLayout.HEIGHT / 2.0) * scale();
    }

    private Point2D worldPoint(double x, double y) {
        return new Point2D.Double((x - screenX(0)) / scale(), (y - screenY(0)) / scale());
    }

    private long pulseAge() {
        return (System.nanoTime() - pulseStarted) / 1_000_000;
    }

    @Override
    protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        Graphics2D graphics = (Graphics2D) original.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Graphics2D scene = (Graphics2D) graphics.create();
        scene.translate(screenX(0), screenY(0));
        scene.scale(scale(), scale());
        drawTerrain(scene);
        CampusMapLayout.buildings(campus).forEach(entry -> drawBuilding(scene, entry));
        scene.dispose();
        drawAreaNames(graphics);
        if (fix != null) {
            drawFix(graphics);
        } else if (selected != null) {
            var anchor = CampusMapLayout.footprint(selected).anchor();
            drawCaption(graphics, screenX(anchor.x), screenY(anchor.y), selected.displayName(), DemoStyle.ACCENT);
        }
        drawCompass(graphics);
        drawScaleAndAttribution(graphics);
        graphics.dispose();
    }

    private void drawTerrain(Graphics2D g) {
        var terrain = CampusMapLayout.forCampus(campus).terrain();
        for (var feature : terrain) {
            var shape = feature.shape();
            switch (feature.layer()) {
                case CAMPUS -> {
                    g.setColor(CAMPUS);
                    g.fill(shape);
                    g.setColor(ROAD_EDGE);
                    g.setStroke(new BasicStroke(1.1f));
                    g.draw(shape);
                }
                case ROAD, STREET -> {
                    boolean major = feature.layer() == CampusMapLayout.Layer.STREET;
                    g.setStroke(new BasicStroke(major ? 4.6f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(major ? new Color(0xFDF7E2) : ROAD);
                    g.draw(shape);
                }
                case TRACK -> {
                    g.setColor(new Color(0xCB8F7D));
                    g.setStroke(new BasicStroke(4));
                    g.draw(shape);
                }
                default -> {
                    g.setColor(switch (feature.layer()) {
                        case WATER -> new Color(0xADD3DC);
                        case GREEN -> GREEN;
                        case FIELD -> new Color(0xABCBA6);
                        default -> new Color(0xC9D2CA);
                    });
                    g.fill(shape);
                }
            }
        }
        var drawn = new HashSet<String>();
        g.setColor(new Color(0x537066));
        g.setFont(getFont().deriveFont(13f));
        for (var feature : terrain) {
            boolean label = feature.layer() == CampusMapLayout.Layer.TRACK || LABELED_STREETS.contains(feature.label());
            if (label && !feature.label().isBlank() && drawn.add(feature.label())) {
                g.drawString(feature.label(), (float) feature.anchor().x + 5, (float) feature.anchor().y - 5);
            }
        }
    }

    private void drawBuilding(Graphics2D g, Footprint entry) {
        CampusBuilding building = entry.building();
        boolean active = building == selected;
        boolean over = building == hovered;
        var shape = entry.shape();
        g.setColor(active ? new Color(0xC6E8D3) : over ? new Color(0xEAF7EB) : buildingColor(building.kind()));
        g.fill(shape);
        g.setColor(active ? DemoStyle.ACCENT : over ? new Color(0x487862) : new Color(0x92A99C));
        g.setStroke(new BasicStroke(active ? 3.2f : over ? 2.5f : 1.2f));
        g.draw(shape);
        boolean overview = zoom < 1.55;
        if ((overview && campus != Campus.NANHU) || active) {
            return;
        }
        g.setColor(INK);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        String text = building.kind() == Kind.DORM ? building.label().replace("宿舍", "") : building.label();
        if (overview || g.getFontMetrics().stringWidth(text) > shape.getBounds2D().getWidth() - 2) {
            text = building.name();
        }
        var metrics = g.getFontMetrics();
        float x = (float) entry.anchor().x - metrics.stringWidth(text) / 2f;
        float y = (float) entry.anchor().y + (metrics.getAscent() - metrics.getDescent()) / 2f;
        g.drawString(text, x, y);
    }

    static Color buildingColor(Kind kind) {
        return switch (kind) {
            case DORM -> new Color(0xA9C8E4);
            case TEACHING -> new Color(0xF1D39D);
            case LIBRARY -> new Color(0xD8BCDC);
            case DINING -> new Color(0xEEC5B2);
        };
    }

    private void drawAreaNames(Graphics2D g) {
        if (zoom >= 1.55) {
            return;
        }
        if (campus == Campus.GUIZISHAN) {
            drawAreaName(g, CampusBuilding.D03, "西区宿舍");
            drawAreaName(g, CampusBuilding.E04, "东区宿舍");
        } else {
            drawAreaName(g, CampusBuilding.N06, "南湖宿舍");
        }
    }

    private void drawAreaName(Graphics2D g, CampusBuilding building, String text) {
        var anchor = CampusMapLayout.footprint(building).anchor();
        int x = (int) screenX(anchor.x);
        int y = (int) screenY(anchor.y) - 13;
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int width = g.getFontMetrics().stringWidth(text) + 14;
        g.setColor(new Color(0xE1EEF8));
        g.fillRoundRect(x - width / 2, y - 17, width, 21, 9, 9);
        g.setColor(INK);
        g.drawString(text, x - width / 2 + 7, y - 2);
    }

    private void drawCompass(Graphics2D g) {
        int x = getWidth() - 32;
        int y = 31;
        g.setColor(new Color(255, 255, 255, 225));
        g.fillOval(x - 19, y - 16, 38, 52);
        g.setColor(INK);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("北", x - 6, y);
        g.fillPolygon(new int[]{x, x - 6, x, x + 6}, new int[]{y + 5, y + 21, y + 17, y + 21}, 4);
    }

    private void drawFix(Graphics2D g) {
        double x = screenX(fix.x());
        double y = screenY(fix.y());
        long age = pulseAge();
        if (age < PULSE_DURATION_MS) {
            double phase = age % 900 / 900.0;
            double radius = 8 + phase * 31;
            g.setColor(new Color(22, 105, 207, (int) ((1 - phase) * 150)));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
        }
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
        g.setColor(BLUE);
        g.fill(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
        drawCaption(g, x, y, "已定位：" + fix.building().displayName(), BLUE);
    }

    private void drawCaption(Graphics2D g, double x, double y, String caption, Color accent) {
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int width = g.getFontMetrics().stringWidth(caption) + 24;
        double left = Math.max(8, Math.min(x - width / 2.0, getWidth() - width - 8));
        double top = Math.max(8, Math.min(y - 46, getHeight() - 37));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(left, top, width, 30, 13, 13));
        g.setColor(accent);
        g.drawString(caption, (float) left + 12, (float) top + 20);
    }

    private void drawScaleAndAttribution(Graphics2D g) {
        g.setFont(getFont().deriveFont(11f));
        g.setColor(INK);
        double units = CampusMapLayout.forCampus(campus).metresToMapUnits(100) * scale();
        int metres = units > 135 ? 50 : 100;
        double length = units * metres / 100;
        double y = getHeight() - 14.0;
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Line2D.Double(14, y, 14 + length, y));
        g.draw(new Line2D.Double(14, y - 4, 14, y + 3));
        g.draw(new Line2D.Double(14 + length, y - 4, 14 + length, y + 3));
        g.drawString(metres + " m", 14, (float) y - 6);
        String source = "© OpenStreetMap contributors";
        int width = g.getFontMetrics().stringWidth(source);
        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(getWidth() - width - 16, getHeight() - 25, width + 9, 18, 7, 7);
        g.setColor(INK);
        g.drawString(source, getWidth() - width - 12, getHeight() - 12);
    }
}
