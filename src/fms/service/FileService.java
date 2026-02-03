package fms.service;

import fms.exception.DuplicateFileException;
import fms.exception.FileNotFoundException;
import fms.exception.FileSystemException;
import fms.model.FileRecord;
import fms.repository.FileRepository;

import java.io.*;
import java.util.*;

/**
 * Business-logic layer.
 *
 * Every disk operation goes through java.io.File  /  FileWriter  /  FileReader.
 * No java.nio anywhere.
 */
public class FileService {

    private final FileRepository repo;

    public FileService(FileRepository repo) {
        this.repo = repo;
    }

    /* ════════════════════ CREATE ══════════════════════════════ */

    /**
     * 1. Validate the name.
     * 2. Make sure the target directory exists (File.mkdirs).
     * 3. Check no duplicate file already lives there.
     * 4. Write the content with FileWriter.
     * 5. Register the record in the repository.
     */
    public FileRecord createFile(String fileName, String content,
                                 String storagePath, String category)
            throws DuplicateFileException, FileSystemException {

        validateFileName(fileName);
        storagePath = storagePath.trim();

        File dir = new File(storagePath);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                throw new FileSystemException("Cannot create directory: " + storagePath);
        }

        File target = new File(dir, fileName);
        if (target.exists())
            throw new DuplicateFileException(
                    "A file named '" + fileName + "' already exists at: " + storagePath);

        // ── write to disk ──
        writeToFile(target, (content == null) ? "" : content);

        // ── persist metadata ──
        FileRecord record = new FileRecord(fileName,
                (content == null) ? "" : content, storagePath, category);
        try {
            repo.save(record);
        } catch (IOException e) {
            throw new FileSystemException(
                    "File written to disk but registry update failed.", e);
        }
        return record;
    }

    /* ════════════════════ READ ════════════════════════════════ */

    /** Resolve by full ID or by the first-8-char prefix. */
    public FileRecord readById(String id) throws FileNotFoundException {
        FileRecord rec = repo.findById(id);          // exact match first
        if (rec == null) {
            // prefix search
            for (FileRecord r : repo.findAll()) {
                if (r.getId().startsWith(id)) {
                    rec = r;
                    break;
                }
            }
        }
        if (rec == null)
            throw new FileNotFoundException("No file found with ID: " + id);
        return rec;
    }

    public FileRecord readByFileName(String fileName) throws FileNotFoundException {
        FileRecord rec = repo.findByFileName(fileName);
        if (rec == null)
            throw new FileNotFoundException("No file found with name: " + fileName);
        return rec;
    }

    public List<FileRecord> readAll() {
        return repo.findAll();
    }

    public List<FileRecord> readByCategory(String category) {
        return repo.findByCategory(category);
    }

    /**
     * Read the LIVE bytes off disk (ignores whatever is cached in memory).
     */
    public String readContentFromDisk(FileRecord record) throws FileSystemException {
        File f = record.toFile();
        if (!f.exists())
            throw new FileSystemException("File does not exist on disk: " + f.getPath());
        return readFromFile(f);
    }

    /* ════════════════════ UPDATE ══════════════════════════════ */

    /** Overwrite content on disk and update the registry. */
    public void updateContent(String id, String newContent)
            throws FileNotFoundException, FileSystemException {

        FileRecord record = readById(id);
        File target = record.toFile();

        writeToFile(target, (newContent == null) ? "" : newContent);

        record.setContent((newContent == null) ? "" : newContent);
        try {
            repo.update(record);
        } catch (IOException e) {
            throw new FileSystemException("File updated on disk but registry update failed.", e);
        }
    }

    /** Rename the file on disk (File.renameTo) and update the registry. */
    public void renameFile(String id, String newFileName)
            throws FileNotFoundException, FileSystemException, DuplicateFileException {

        validateFileName(newFileName);
        FileRecord record = readById(id);

        File oldFile = record.toFile();
        File newFile = new File(record.getStoragePath(), newFileName);

        if (newFile.exists())
            throw new DuplicateFileException(
                    "A file named '" + newFileName + "' already exists at: " + record.getStoragePath());

        if (!oldFile.renameTo(newFile))
            throw new FileSystemException("Failed to rename file on disk.");

        record.setFileName(newFileName);
        try {
            repo.update(record);
        } catch (IOException e) {
            throw new FileSystemException("File renamed on disk but registry update failed.", e);
        }
    }

    /** Move the file to a different directory. */
    public void moveFile(String id, String newStoragePath)
            throws FileNotFoundException, FileSystemException, DuplicateFileException {

        FileRecord record = readById(id);
        newStoragePath = newStoragePath.trim();

        File newDir = new File(newStoragePath);
        if (!newDir.exists()) {
            if (!newDir.mkdirs())
                throw new FileSystemException("Cannot create target directory: " + newStoragePath);
        }

        File target = new File(newDir, record.getFileName());
        if (target.exists())
            throw new DuplicateFileException(
                    "A file named '" + record.getFileName() + "' already exists at: " + newStoragePath);

        File oldFile = record.toFile();
        if (!oldFile.renameTo(target))
            throw new FileSystemException("Failed to move file on disk.");

        record.setStoragePath(newStoragePath);
        try {
            repo.update(record);
        } catch (IOException e) {
            throw new FileSystemException("File moved on disk but registry update failed.", e);
        }
    }

    /** Change category tag only – no disk I/O needed. */
    public void updateCategory(String id, String newCategory)
            throws FileNotFoundException, FileSystemException {

        FileRecord record = readById(id);
        record.setCategory(newCategory);
        try {
            repo.update(record);
        } catch (IOException e) {
            throw new FileSystemException("Registry update failed.", e);
        }
    }

    /* ════════════════════ DELETE ═══════════════════════════════ */

    /** Delete the physical file (File.delete) then remove from registry. */
    public void deleteFile(String id)
            throws FileNotFoundException, FileSystemException {

        FileRecord record = readById(id);
        File f = record.toFile();

        if (f.exists()) {
            if (!f.delete())
                throw new FileSystemException("Failed to delete file from disk: " + f.getPath());
        }

        try {
            repo.delete(record.getId());
        } catch (IOException e) {
            throw new FileSystemException("File deleted from disk but registry update failed.", e);
        }
    }

    /* ════════════════════ PRIVATE HELPERS ═════════════════════ */

    /** Write (or overwrite) a File using FileWriter + BufferedWriter. */
    private static void writeToFile(File file, String content) throws FileSystemException {
        FileWriter   fw = null;
        BufferedWriter bw = null;
        try {
            fw = new FileWriter(file);                // false = overwrite (default)
            bw = new BufferedWriter(fw);
            bw.write(content);
            bw.flush();
        } catch (IOException e) {
            throw new FileSystemException("Failed to write file: " + file.getPath(), e);
        } finally {
            try { if (bw != null) bw.close(); } catch (IOException ignored) {}
            try { if (fw != null) fw.close(); } catch (IOException ignored) {}
        }
    }

    /** Read the entire contents of a File using FileReader + BufferedReader. */
    private static String readFromFile(File file) throws FileSystemException {
        FileReader    fr = null;
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            fr = new FileReader(file);
            br = new BufferedReader(fr);
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (!first) sb.append("\n");
                sb.append(line);
                first = false;
            }
        } catch (IOException e) {
            throw new FileSystemException("Failed to read file: " + file.getPath(), e);
        } finally {
            try { if (br != null) br.close(); } catch (IOException ignored) {}
            try { if (fr != null) fr.close(); } catch (IOException ignored) {}
        }
        return sb.toString();
    }

    /** Basic name validation – no OS-illegal chars, must have extension. */
    private static void validateFileName(String name) throws FileSystemException {
        if (name == null || name.trim().isEmpty())
            throw new FileSystemException("File name cannot be empty.");

        String illegal = "";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ("<>:\"/\\|?*".indexOf(c) >= 0)
                illegal += c;
        }
        if (!illegal.isEmpty())
            throw new FileSystemException("File name contains illegal characters: " + illegal);

        if (!name.contains("."))
            throw new FileSystemException("File name must include an extension (e.g. notes.txt).");
    }
}
