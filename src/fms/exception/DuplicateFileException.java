package fms.exception;

public class DuplicateFileException extends Exception {
    private static final long serialVersionUID = 1L;
    public DuplicateFileException(String msg) { super(msg); }
}
