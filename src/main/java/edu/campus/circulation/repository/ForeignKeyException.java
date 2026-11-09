package edu.campus.circulation.repository;

public class ForeignKeyException extends DataAccessException {
    public ForeignKeyException(String message) {
        super(message);
    }

    public ForeignKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
