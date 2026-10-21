package me.luucka.travelplanner.common.storage;

import me.luucka.travelplanner.common.ApiException;
import org.springframework.http.HttpStatus;

// Mapped to 415 + code UNSUPPORTED_FILE_TYPE by ApiExceptionHandler's ApiException handler.
public class UnsupportedFileTypeException extends ApiException {

    public UnsupportedFileTypeException() {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Unsupported file type");
    }
}
