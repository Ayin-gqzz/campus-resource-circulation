package edu.campus.circulation.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DemoStyleTest {
    @Test
    void comboArrowClearsStalePixelsBeforeDrawingAtDifferentSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DemoStyle.install();
            JComboBox<String> combo = new JComboBox<>(new String[]{"陈同学", "林同学"});
            DemoStyle.field(combo);
            JButton arrow = null;
            for (Component child : combo.getComponents()) {
                if (child instanceof JButton button) {
                    arrow = button;
                }
            }
            assertNotNull(arrow);
            for (int width : new int[]{27, 38, 57}) {
                arrow.setSize(width, 38);
                BufferedImage canvas = new BufferedImage(width, 38, BufferedImage.TYPE_INT_RGB);
                var graphics = canvas.createGraphics();
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, width, 38);
                arrow.paint(graphics);
                graphics.dispose();
                assertEquals(DemoStyle.SURFACE.getRGB(), canvas.getRGB(1, 1));
                assertEquals(DemoStyle.SURFACE.getRGB(), canvas.getRGB(width - 2, 36));
                arrow.setEnabled(false);
                graphics = canvas.createGraphics();
                arrow.paint(graphics);
                graphics.dispose();
                assertEquals(DemoStyle.CANVAS.getRGB(), canvas.getRGB(1, 1));
                arrow.setEnabled(true);
            }
        });
    }
}
