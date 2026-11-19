package edu.campus.circulation.ui;

import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantityFieldTest {
    @Test
    void validatesTypedQuantitiesAndCalculatesExactTotalsWithoutOpeningAWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var field = new QuantityField(5);
            var input = ((JSpinner.NumberEditor) field.getEditor()).getTextField();
            int[] preview = {0};
            field.onTextChange(value -> preview[0] = value);
            input.setText("3");
            assertEquals(3, preview[0]);
            assertEquals(3, field.quantity());
            for (String invalid : new String[]{"", "0", "6", "-1", "1.5", "1,000", "两本", "10000"}) {
                input.setText(invalid);
                assertEquals(0, preview[0]);
                assertThrows(IllegalArgumentException.class, field::quantity, invalid);
            }
            input.setText("5");
            assertEquals(5, field.quantity());
            assertTrue(DemoWindow.transactionAmount(10L, 3).endsWith("总价 ¥0.30"));
            assertTrue(DemoWindow.transactionAmount(9000000000000000000L, 9999).endsWith("总价 ¥899910000000000000000.00"));
            assertEquals("换物 · 数量 3 件", DemoWindow.transactionAmount(null, 3));
        });
    }
}
