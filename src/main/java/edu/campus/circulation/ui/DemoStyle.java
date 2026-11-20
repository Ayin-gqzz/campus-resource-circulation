package edu.campus.circulation.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.FontUIResource;

final class DemoStyle {
    static final Color CANVAS = new Color(0xF2F5F0);
    static final Color SIDEBAR = new Color(0xE9EFE8);
    static final Color SURFACE = Color.WHITE;
    static final Color TEXT = new Color(0x192129);
    static final Color MUTED = new Color(0x52606C);
    static final Color LINE = new Color(0xDCE5DC);
    static final Color ACCENT = new Color(0x255E45);
    static final Color TINT = new Color(0xE0EDDF);
    static final Color FOREST = new Color(0x173E30);
    static final Color ON_FOREST = new Color(0xD5E7D8);
    static final Color DANGER = new Color(0x9B382F);

    private DemoStyle() {}

    static void install() {
        var families = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String family = families.contains("Microsoft YaHei UI") ? "Microsoft YaHei UI" : Font.DIALOG;
        FontUIResource font = new FontUIResource(family, Font.PLAIN, 14);
        for (Object key : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (key.toString().endsWith(".font")) {
                UIManager.put(key, font);
            }
        }
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("OptionPane.background", CANVAS);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.selectionBackground", TINT);
        UIManager.put("ComboBox.selectionForeground", ACCENT);
        UIManager.put("ComboBox.foreground", TEXT);
    }

    static JLabel label(String text, float size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    static void field(JComponent field) {
        if (field instanceof JComboBox<?> combo) {
            combo.setOpaque(true);
            combo.setBackground(SURFACE);
            combo.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton arrow = new JButton() {
                        @Override
                        protected void paintComponent(Graphics original) {
                            Graphics2D g = (Graphics2D) original.create();
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            // This child owns its entire bounds. Clear before painting to avoid stale
                            // pixels from sibling controls after popups, resizing, or identity changes.
                            g.setColor(isEnabled() ? SURFACE : CANVAS);
                            g.fillRect(0, 0, getWidth(), getHeight());
                            g.setColor(isEnabled() ? MUTED : LINE);
                            g.setStroke(new java.awt.BasicStroke(1.5f));
                            int x = getWidth() / 2;
                            int y = getHeight() / 2;
                            g.drawLine(x - 4, y - 2, x, y + 2);
                            g.drawLine(x, y + 2, x + 4, y - 2);
                            g.dispose();
                        }
                    };
                    arrow.setPreferredSize(new Dimension(27, 28));
                    arrow.setFocusable(false);
                    arrow.setBorderPainted(false);
                    arrow.setContentAreaFilled(false);
                    arrow.setOpaque(true);
                    arrow.setBorder(BorderFactory.createEmptyBorder());
                    return arrow;
                }
            });
        }
        if (field instanceof JSpinner spinner && spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setHorizontalAlignment(JTextField.LEADING);
            editor.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 5));
        }
        if (field instanceof JComboBox<?> || field instanceof JTextField) {
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LINE, 1, true),
                    BorderFactory.createEmptyBorder(0, 9, 0, 5)));
        }
        Dimension size = field.getPreferredSize();
        field.setPreferredSize(new Dimension(size.width, Math.max(38, size.height)));
        field.setMinimumSize(new Dimension(Math.min(size.width, 110), Math.max(38, size.height)));
    }

    static final class Banner extends JPanel {
        Banner() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var shape = new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26);
            g.setPaint(new GradientPaint(0, 0, FOREST, getWidth(), getHeight(), new Color(0x315F42)));
            g.fill(shape);
            g.clip(shape);
            g.setColor(new Color(214, 239, 202, 37));
            int centerX = getWidth() - 78;
            int centerY = getHeight() + 36;
            for (int radius = 65; radius <= 225; radius += 23) {
                g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            }
            g.dispose();
        }
    }

    static class Surface extends JPanel {
        private final Color fill;
        private final Color outline;
        private final int radius;

        Surface(Color fill, Color outline, int radius) {
            this.fill = fill;
            this.outline = outline;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics original) {
            Graphics2D graphics = (Graphics2D) original.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(fill);
            graphics.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (outline != null) {
                graphics.setColor(outline);
                graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            graphics.dispose();
        }
    }

    enum Role { PRIMARY, SECONDARY, DESTRUCTIVE, NAVIGATION }

    static final class Button extends JButton {
        private Role role;

        Button(String text, Role role) {
            super(text);
            this.role = role;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            if (role == Role.NAVIGATION) {
                setHorizontalAlignment(LEADING);
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            }
        }

        void setRole(Role value) {
            if (role != value) {
                role = value;
                repaint();
            }
        }

        @Override
        public Color getForeground() {
            if (!isEnabled()) {
                return MUTED;
            }
            return switch (role == null ? Role.SECONDARY : role) {
                case PRIMARY -> Color.WHITE;
                case DESTRUCTIVE -> DANGER;
                case NAVIGATION -> isSelected() ? ACCENT : TEXT;
                case SECONDARY -> TEXT;
            };
        }

        @Override
        protected void paintComponent(Graphics original) {
            Graphics2D graphics = (Graphics2D) original.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = switch (role) {
                case PRIMARY -> isEnabled() ? ACCENT : SIDEBAR;
                case NAVIGATION -> isSelected() ? TINT : SIDEBAR;
                case SECONDARY, DESTRUCTIVE -> isEnabled() ? SURFACE : CANVAS;
            };
            if (isEnabled() && getModel().isPressed()) {
                fill = fill.darker();
            } else if (isEnabled() && getModel().isRollover()) {
                fill = role == Role.PRIMARY ? new Color(0x11543C) : TINT;
            }
            graphics.setColor(fill);
            graphics.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (isFocusOwner() || role == Role.SECONDARY || role == Role.DESTRUCTIVE) {
                graphics.setColor(isFocusOwner() ? new Color(0x4085DD) : LINE);
                graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            if (role == Role.PRIMARY && isFocusOwner() && isEnabled()) {
                graphics.setColor(Color.WHITE);
                graphics.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 9, 9);
            }
            graphics.dispose();
            super.paintComponent(original);
        }
    }
}
