package me.luucka.travelplanner.common;

/** Missing or not owned by the caller; both look the same to the client. */
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super("Not found");
    }
}
