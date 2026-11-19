package edu.campus.circulation.ui;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.TimeZone;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeTimeFieldTest {
    @Test
    void parsesBeijingTimeAndRejectsImpossibleDatesWithoutOpeningAWindow() throws Exception {
        var previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            SwingUtilities.invokeAndWait(() -> {
                var field = new TradeTimeField();
                var input = ((JSpinner.DateEditor) field.getEditor()).getTextField();
                field.setInstant(Instant.parse("2032-02-29T15:30:00Z"));
                assertEquals("2032-02-29 23:30", input.getText());
                assertEquals(Instant.parse("2032-02-29T15:30:00Z"), field.instant());
                for (String invalid : new String[]{"", "2031-02-29 12:00", "2032-04-31 12:00", "2032-02-29 24:70",
                        "明天下午", "2032-02-29 12:00abc", "2032-2-9 12:00"}) {
                    input.setText(invalid);
                    assertThrows(IllegalArgumentException.class, field::instant, invalid);
                }
                input.setText("2032-03-01 00:05");
                assertEquals(Instant.parse("2032-02-29T16:05:00Z"), field.instant());
                field.setSize(280, 40);
                field.doLayout();
                ((JSpinner.DateEditor) field.getEditor()).doLayout();
                assertTrue(input.getWidth() - input.getInsets().left - input.getInsets().right
                        >= input.getFontMetrics(input.getFont()).stringWidth(input.getText()));
                var preview = new BufferedImage(280, 40, BufferedImage.TYPE_INT_ARGB);
                var graphics = preview.createGraphics();
                try { field.paint(graphics); } finally { graphics.dispose(); }
                assertEquals(280, preview.getWidth());
            });
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
