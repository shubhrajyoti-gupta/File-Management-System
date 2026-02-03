package fms.model;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;
import java.text.SimpleDateFormat;


public class FileRecord implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat FMT  = new SimpleDateFormat("dd-MMM-yyyy HH:mm");

    private final String id;
    private String fileName;
    private String content;
    private String storagePath;  
    private String category;
    private Date   createdAt;
    private Date   updatedAt;

    public FileRecord(String fileName, String content,
                      String storagePath, String category) {
        this.id          = UUID.randomUUID().toString();
        this.fileName    = fileName;
        this.content     = (content == null) ? "" : content;
        this.storagePath = storagePath;
        this.category    = (category == null || category.trim().isEmpty())
                           ? "General" : category.trim();
        this.createdAt   = new Date();
        this.updatedAt   = new Date(createdAt.getTime());
    }

    public FileRecord(String id, String fileName, String content,
                      String storagePath, String category,
                      Date createdAt, Date updatedAt) {
        this.id          = id;
        this.fileName    = fileName;
        this.content     = (content == null) ? "" : content;
        this.storagePath = storagePath;
        this.category    = (category == null || category.trim().isEmpty())
                           ? "General" : category.trim();
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    public String getId()           { return id; }
    public String getFileName()     { return fileName; }
    public String getContent()      { return content; }
    public String getStoragePath()  { return storagePath; }
    public String getCategory()     { return category; }
    public Date   getCreatedAt()    { return createdAt; }
    public Date   getUpdatedAt()    { return updatedAt; }


    public void setFileName(String fileName) {
        this.fileName  = fileName;
        this.updatedAt = new Date();
    }
    public void setContent(String content) {
        this.content   = (content == null) ? "" : content;
        this.updatedAt = new Date();
    }
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
        this.updatedAt   = new Date();
    }
    public void setCategory(String category) {
        this.category  = (category == null || category.trim().isEmpty())
                         ? "General" : category.trim();
        this.updatedAt = new Date();
    }
    public File toFile() {
        return new File(storagePath, fileName);
    }

    public String getShortId() {
        return id.substring(0, 8);
    }

    @Override
    public String toString() {
        return String.format("[%s] %-28s | %-12s | %s | %s",
                getShortId(), fileName, category,
                FMT.format(createdAt), storagePath);
    }
}
