package edu.campus.circulation.ui;

import static edu.campus.circulation.domain.CampusBuilding.D05;
import static edu.campus.circulation.domain.CampusBuilding.N05;
import static edu.campus.circulation.domain.MeetupLocation.Campus.GUIZISHAN;
import static edu.campus.circulation.domain.MeetupLocation.Campus.NANHU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BuildingPickerDialogTest {
    @Test
    void refreshesChoicesWhenSwitchingToNanhuAndConfirmsWithoutLocating() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var closed = new AtomicBoolean();
            var picker = new BuildingPickerDialog(GUIZISHAN, D05, () -> closed.set(true));
            var campus = (JComboBox<?>) labeled(picker.content(), "校区");
            var buildings = (JComboBox<?>) labeled(picker.content(), "选择楼栋");
            var confirm = confirmButton(picker.content());
            assertEquals(D05.displayName(), buildings.getSelectedItem().toString());
            assertTrue(confirm.isEnabled());

            campus.setSelectedItem(NANHU);
            assertEquals(17, buildings.getItemCount());
            assertEquals("请选择楼栋", buildings.getSelectedItem().toString());
            assertFalse(confirm.isEnabled());

            for (int index = 1; index < buildings.getItemCount(); index++) {
                if (N05.displayName().equals(buildings.getItemAt(index).toString())) {
                    buildings.setSelectedIndex(index);
                    break;
                }
            }
            assertTrue(confirm.isEnabled());
            confirm.doClick(0);
            assertEquals(N05, picker.result().orElseThrow());
            assertTrue(closed.get());
        });
    }

    @Test
    void chineseDormitorySearchSynchronizesTheDropdownAndConfirmation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var picker = new BuildingPickerDialog(GUIZISHAN, null, () -> {});
            ((JComboBox<?>) labeled(picker.content(), "校区")).setSelectedItem(NANHU);
            ((JTextField) labeled(picker.content(), "搜索楼栋")).setText("南湖五栋");
            var buildings = (JComboBox<?>) labeled(picker.content(), "选择楼栋");
            assertEquals(N05.displayName(), buildings.getSelectedItem().toString());
            confirmButton(picker.content()).doClick(0);
            assertEquals(N05, picker.result().orElseThrow());
        });
    }

    private static Component labeled(Container root, String text) {
        return find(root, JLabel.class, label -> text.equals(label.getText())).getLabelFor();
    }

    private static JButton confirmButton(Container root) {
        return find(root, JButton.class, button -> "确认楼栋".equals(button.getText()));
    }

    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> matches) {
        return children(root).filter(type::isInstance).map(type::cast).filter(matches).findFirst().orElseThrow();
    }

    private static Stream<Component> children(Container root) {
        return Arrays.stream(root.getComponents()).flatMap(child -> child instanceof Container group
                ? Stream.concat(Stream.of(child), children(group)) : Stream.of(child));
    }
}
