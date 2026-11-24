package edu.campus.circulation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CampusMapPanelTest {
    @ParameterizedTest
    @EnumSource(Campus.class)
    void rendersAndSelectsTheDormitoryAtItsProjectedPosition(Campus campus) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var picked = new AtomicReference<CampusBuilding>();
            Campus previousCampus = campus == Campus.NANHU ? Campus.GUIZISHAN : Campus.NANHU;
            var panel = new CampusMapPanel(previousCampus, picked::set);
            panel.setSize(680, 472);
            panel.zoom(2);
            panel.showCampus(campus);
            var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }

            CampusBuilding building = campus == Campus.NANHU ? CampusBuilding.N05 : CampusBuilding.D05;
            var anchor = CampusMapLayout.footprint(building).anchor();
            double scale = Math.min(panel.getWidth() / (double) CampusMapLayout.WIDTH,
                    panel.getHeight() / (double) CampusMapLayout.HEIGHT);
            int x = (int) Math.round(panel.getWidth() / 2.0 + (anchor.x - CampusMapLayout.WIDTH / 2.0) * scale);
            int y = (int) Math.round(panel.getHeight() / 2.0 + (anchor.y - CampusMapLayout.HEIGHT / 2.0) * scale);

            int dormitoryColor = CampusMapPanel.buildingColor(CampusBuilding.Kind.DORM).getRGB();
            boolean visible = false;
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    visible |= image.getRGB(x + dx, y + dy) == dormitoryColor;
                }
            }
            assertTrue(visible, "宿舍楼应被绘制在可见的地图区域内");

            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
            assertEquals(building, picked.get());
        });
    }
}
