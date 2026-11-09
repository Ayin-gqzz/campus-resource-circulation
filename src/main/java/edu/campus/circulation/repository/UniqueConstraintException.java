package edu.campus.circulation.repository;

public class UniqueConstraintException extends DataAccessException {
    public UniqueConstraintException(String message) {
        super(message);
    }

    public UniqueConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
