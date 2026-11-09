package edu.campus.circulation.repository;

import java.sql.SQLException;
import java.util.Locale;

public final class SqlExceptions {
    private SqlExceptions() {
    }

    public static DataAccessException translate(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int code = current.getErrorCode();
            String message = current.getMessage() == null ? "" : current.getMessage().toUpperCase(Locale.ROOT);
            if (code == 2067 || code == 1555 || message.contains("SQLITE_CONSTRAINT_UNIQUE")
                    || message.contains("SQLITE_CONSTRAINT_PRIMARYKEY")
                    || message.contains("UNIQUE CONSTRAINT FAILED")
                    || message.contains("PRIMARY KEY CONSTRAINT FAILED")) {
                return new UniqueConstraintException(exception.getMessage(), exception);
            }
            if (code == 787 || message.contains("SQLITE_CONSTRAINT_FOREIGNKEY")
                    || message.contains("FOREIGN KEY CONSTRAINT FAILED")) {
                return new ForeignKeyException(exception.getMessage(), exception);
            }
        }
        return new DataAccessException(exception.getMessage(), exception);
    }
}
