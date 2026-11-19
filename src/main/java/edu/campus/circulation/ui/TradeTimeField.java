package edu.campus.circulation.ui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import javax.swing.JSpinner;
import javax.swing.JFormattedTextField;
import javax.swing.SpinnerDateModel;

/** Edits a minute-precision appointment in campus time, independently of the computer's timezone. */
final class TradeTimeField extends JSpinner {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    TradeTimeField() {
        super(new SpinnerDateModel(Date.from(Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)),
                null, null, Calendar.MINUTE));
        DateEditor editor = new DateEditor(this, "yyyy-MM-dd HH:mm");
        editor.getFormat().setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        editor.getFormat().setLenient(false);
        editor.getTextField().setColumns(17);
        editor.getTextField().setFocusLostBehavior(JFormattedTextField.PERSIST);
        editor.getTextField().setToolTipText("北京时间，例如 2026-11-20 18:30；可直接输入或用箭头调整。");
        setEditor(editor);
        getAccessibleContext().setAccessibleName("交易时间（北京时间）");
        editor.getTextField().setValue(getValue());
    }

    void setInstant(Instant value) {
        if (value == null) ((DateEditor) getEditor()).getTextField().setText("");
        else {
            setValue(Date.from(value));
            ((DateEditor) getEditor()).getTextField().setValue(getValue());
        }
    }

    Instant instant() {
        if (((DateEditor) getEditor()).getTextField().getText().isBlank()) {
            throw new IllegalArgumentException("请填写交易时间（北京时间）。");
        }
        try {
            Instant value = LocalDateTime.parse(((DateEditor) getEditor()).getTextField().getText().strip(), INPUT)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
            setInstant(value);
            return value;
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("请输入有效的交易时间，格式为 yyyy-MM-dd HH:mm。", invalid);
        }
    }
}
