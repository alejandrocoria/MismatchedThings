package games.alejandrocoria.mismatchedboots.data;

/** Installation/safety refusal; bounded unreadable archive contents are quarantined instead. */
public final class RecoveryRefusalException extends IllegalStateException {
    public RecoveryRefusalException(String message) { super(message); }
    public RecoveryRefusalException(String message, Throwable cause) { super(message, cause); }
}
