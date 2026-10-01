package land.momo.nekocore.data;

public final class StorageException extends RuntimeException {
    private final String messageKey;
    public StorageException(String messageKey) { super(messageKey); this.messageKey = messageKey; }
    public String messageKey() { return messageKey; }
}
