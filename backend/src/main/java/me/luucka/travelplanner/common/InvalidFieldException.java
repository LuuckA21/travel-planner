package me.luucka.travelplanner.common;

/** A request that is well-formed but invalid against stored state; reported under {@code errors.<field>}. */
public class InvalidFieldException extends RuntimeException {
    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
