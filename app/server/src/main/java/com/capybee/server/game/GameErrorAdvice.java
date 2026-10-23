package com.capybee.server.game;

import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes.ErrorCode;
import com.capybee.engine.session.wire.ErrorDto;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = GameRunController.class)
public class GameErrorAdvice {
    private static final System.Logger LOGGER = System.getLogger(GameErrorAdvice.class.getName());

    @ExceptionHandler(SessionException.class)
    public ResponseEntity<ErrorDto> sessionError(SessionException exception) {
        ErrorCode code = exception.code();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status(code));
        if (code == ErrorCode.RUN_BUSY) {
            response.header("Retry-After", "1");
        } else if (code == ErrorCode.RATE_LIMITED) {
            Object seconds = exception.details().get("retryAfterSeconds");
            response.header("Retry-After", seconds == null ? "60" : seconds.toString());
        }
        return response.body(new ErrorDto(new ErrorDto.Error(code.name(), message(code), exception.details())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorDto> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.REQUEST_INVALID.name(),
                "The request is invalid.", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorDto> databaseUnavailable(Exception exception) {
        LOGGER.log(System.Logger.Level.WARNING, "Game API persistence failure; code=SERVICE_UNAVAILABLE");
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.name(),
                "The service is temporarily unavailable.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> unexpected(Exception exception) {
        LOGGER.log(System.Logger.Level.ERROR, "Game API request failed; code=INTERNAL_ERROR");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "Something went wrong.", Map.of());
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String code, String message,
                                                   Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ErrorDto(
                new ErrorDto.Error(code, message, details)));
    }

    private static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID, PURGE_CONFIRMATION_REQUIRED -> HttpStatus.BAD_REQUEST;
            case GAME_DISABLED, RUN_NOT_FOUND, RUN_ARCHIVED, RUN_TRASHED -> HttpStatus.NOT_FOUND;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case RUN_ALREADY_ACTIVE, RUN_BUSY, RUN_NOT_ACTIVE, REVISION_CONFLICT,
                    GENERATION_UNAVAILABLE, RUN_SUSPENDED, RUN_QUARANTINED, RUN_NOT_RESUMABLE,
                    RUN_SWITCH_BUSY, COMMAND_INTERRUPTED, PACK_VERSION_MISMATCH -> HttpStatus.CONFLICT;
            case COMMAND_ID_REUSED, START_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case GENERATION_BUSY, RECOVERY_IN_PROGRESS, SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR, START_FAILED, START_TIMEOUT, RUN_UNRECOVERABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String message(ErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> "The request is invalid.";
            case PURGE_CONFIRMATION_REQUIRED -> "Confirmation is required to permanently delete the run.";
            case GAME_DISABLED -> "Game sessions are unavailable.";
            case PAYLOAD_TOO_LARGE -> "The request is too large.";
            case RUN_NOT_FOUND -> "The run was not found.";
            case RUN_ARCHIVED -> "The run is archived.";
            case RUN_TRASHED -> "The run is in the trash.";
            case RUN_ALREADY_ACTIVE -> "A run is already active.";
            case RUN_BUSY, GENERATION_BUSY, RUN_SWITCH_BUSY -> "The run is busy. Please try again.";
            case RUN_NOT_ACTIVE -> "The run is not active.";
            case RUN_SUSPENDED -> "The run is suspended.";
            case RUN_QUARANTINED -> "The run is unavailable.";
            case RUN_NOT_RESUMABLE -> "The run cannot be resumed.";
            case REVISION_CONFLICT -> "The run has changed.";
            case COMMAND_ID_REUSED -> "This command id was already used.";
            case COMMAND_INTERRUPTED -> "The previous command was interrupted.";
            case PACK_VERSION_MISMATCH -> "The game pack version has changed.";
            case START_REJECTED, START_FAILED -> "The run could not be started.";
            case START_TIMEOUT, RUN_UNRECOVERABLE -> "The run could not be recovered.";
            case GENERATION_UNAVAILABLE -> "Generated play is unavailable.";
            case RATE_LIMITED -> "Please wait before trying again.";
            case RECOVERY_IN_PROGRESS -> "The service is recovering sessions. Please try again.";
            case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable.";
            case INTERNAL_ERROR -> "Something went wrong.";
        };
    }
}
