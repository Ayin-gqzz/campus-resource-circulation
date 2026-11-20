package edu.campus.circulation.ui;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

final class QuantityField extends JSpinner {
    private final int maximum;

    QuantityField(int maximum) {
        super(new SpinnerNumberModel(1, 1, maximum, 1));
        this.maximum = maximum;
        var editor = new NumberEditor(this, "0");
        editor.getTextField().setColumns(6);
        editor.getTextField().setFocusLostBehavior(JFormattedTextField.PERSIST);
        setEditor(editor);
        getAccessibleContext().setAccessibleName("数量");
    }

    int quantity() {
        int value = parsedQuantity();
        setValue(value);
        return value;
    }

    private int parsedQuantity() {
        String text = ((NumberEditor) getEditor()).getTextField().getText().strip();
        if (!text.matches("[0-9]{1,4}")) throw new IllegalArgumentException("数量须为 1–" + maximum + " 的整数。");
        int value = Integer.parseInt(text);
        if (value < 1 || value > maximum) throw new IllegalArgumentException("数量须为 1–" + maximum + " 的整数。");
        return value;
    }

    void onTextChange(java.util.function.IntConsumer callback) {
        ((NumberEditor) getEditor()).getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                int value;
                try { value = parsedQuantity(); } catch (IllegalArgumentException invalid) { value = 0; }
                callback.accept(value);
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }
}
