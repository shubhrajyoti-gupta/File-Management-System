package fms.exception;

public class FileSystemException extends Exception {
    private static final long serialVersionUID = 1L;
    public FileSystemException(String msg)              { super(msg); }
    public FileSystemException(String msg, Throwable c) { super(msg, c); }
}
