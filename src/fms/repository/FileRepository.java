package fms.repository;

import fms.model.FileRecord;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;


public class FileRepository {

    private static final String DELIMITER = "|";
    private static final SimpleDateFormat DT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private final File registryFile;                          
    private final Map<String, FileRecord> store              
            = new LinkedHashMap<>();


    public FileRepository(String registryDir) throws IOException {
        File dir = new File(registryDir);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                throw new IOException("Cannot create registry directory: " + registryDir);
        }
        this.registryFile = new File(dir, "fms_registry.dat");
        loadFromDisk();
    }

   

    public void save(FileRecord record) throws IOException {
        store.put(record.getId(), record);
        persistToDisk();
    }

    public FileRecord findById(String id) {
        return store.get(id);
    }

    public FileRecord findByFileName(String fileName) {
        for (FileRecord r : store.values()) {
            if (r.getFileName().equalsIgnoreCase(fileName))
                return r;
        }
        return null;
    }

    public List<FileRecord> findAll() {
        List<FileRecord> list = new ArrayList<>(store.values());
        Collections.sort(list, new Comparator<FileRecord>() {
            @Override
            public int compare(FileRecord a, FileRecord b) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());   
            }
        });
        return list;
    }

    public List<FileRecord> findByCategory(String category) {
        List<FileRecord> result = new ArrayList<>();
        for (FileRecord r : store.values()) {
            if (r.getCategory().equalsIgnoreCase(category))
                result.add(r);
        }
        Collections.sort(result, new Comparator<FileRecord>() {
            @Override
            public int compare(FileRecord a, FileRecord b) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
        });
        return result;
    }

    public void update(FileRecord record) throws IOException {
        store.put(record.getId(), record);
        persistToDisk();
    }

    public boolean delete(String id) throws IOException {
        boolean removed = (store.remove(id) != null);
        if (removed) persistToDisk();
        return removed;
    }

    public int count() { return store.size(); }

    
    private void persistToDisk() throws IOException {
        File tmpFile = new File(registryFile.getParent(), "fms_registry.tmp");

        FileWriter  fw = new FileWriter(tmpFile);           
        BufferedWriter bw = new BufferedWriter(fw);

        for (FileRecord r : store.values()) {
            bw.write(toLine(r));
            bw.newLine();
        }
        bw.flush();
        bw.close();                                        

        if (registryFile.exists())
            registryFile.delete();
        if (!tmpFile.renameTo(registryFile))
            throw new IOException("Failed to rename tmp registry to final.");
    }


    private void loadFromDisk() throws IOException {
        if (!registryFile.exists()) return;                
        FileReader   fr = new FileReader(registryFile);
        BufferedReader br = new BufferedReader(fr);

        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            FileRecord rec = fromLine(line);
            store.put(rec.getId(), rec);
        }
        br.close();
    }

 
    private static String toLine(FileRecord r) {
        String safeContent = r.getContent()
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("|", "\\p");

        return r.getId()            + DELIMITER +
               r.getFileName()      + DELIMITER +
               r.getStoragePath()   + DELIMITER +
               r.getCategory()      + DELIMITER +
               DT.format(r.getCreatedAt()) + DELIMITER +
               DT.format(r.getUpdatedAt()) + DELIMITER +
               safeContent;
    }

    private static FileRecord fromLine(String line) throws IOException {
     
        String[] parts = line.split("\\|", 7);
        if (parts.length < 7)
            throw new IOException("Corrupt registry line (expected 7 fields): " + line);

        String id          = parts[0].trim();
        String fileName    = parts[1].trim();
        String storagePath = parts[2].trim();
        String category    = parts[3].trim();
        Date   createdAt;
        Date   updatedAt;
        try {
            createdAt = DT.parse(parts[4].trim());
            updatedAt = DT.parse(parts[5].trim());
        } catch (ParseException e) {
            throw new IOException("Cannot parse date in registry line.", e);
        }

        String content = parts[6]
                .replace("\\p", "|")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");

        return new FileRecord(id, fileName, content, storagePath, category, createdAt, updatedAt);
    }
}
