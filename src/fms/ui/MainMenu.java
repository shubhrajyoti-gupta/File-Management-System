package fms.ui;

import fms.exception.DuplicateFileException;
import fms.exception.FileNotFoundException;
import fms.exception.FileSystemException;
import fms.model.FileRecord;
import fms.service.FileService;
import fms.util.ConsoleUtil;

import java.io.File;
import java.util.*;

/**
 * Interactive console-menu driver.
 *
 * Menu
 * ────
 *   1  Create File
 *   2  List All Files
 *   3  View File
 *   4  Edit File Content
 *   5  Rename File
 *   6  Move File
 *   7  Change Category
 *   8  Delete File
 *   9  List by Category
 *   0  Exit
 */
public class MainMenu {

    private final FileService service;
    private final Scanner     sc;

    public MainMenu(FileService service) {
        this.service = service;
        this.sc      = new Scanner(System.in);
    }

    /* ════════════════ MAIN LOOP ═══════════════════════════════ */

    public void run() {
        ConsoleUtil.banner("FILE MANAGEMENT SYSTEM");
        ConsoleUtil.info("Registry lives in: " + System.getProperty("user.home")
                + File.separator + ".fms_data");
        ConsoleUtil.info("Type a menu number and press Enter.");

        boolean running = true;
        while (running) {
            printMenu();
            ConsoleUtil.prompt("Your choice: ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1":  handleCreate();         break;
                case "2":  handleListAll();        break;
                case "3":  handleView();           break;
                case "4":  handleEditContent();    break;
                case "5":  handleRename();         break;
                case "6":  handleMove();           break;
                case "7":  handleChangeCategory(); break;
                case "8":  handleDelete();         break;
                case "9":  handleListByCategory(); break;
                case "0":
                    ConsoleUtil.banner("GOODBYE!");
                    running = false;
                    break;
                default:
                    ConsoleUtil.warning("Invalid choice. Please enter 0-9.");
            }
        }
        sc.close();
    }

    /* ════════════════ MENU PRINTER ════════════════════════════ */

    private void printMenu() {
        System.out.println();
        ConsoleUtil.separator();
        System.out.println(ConsoleUtil.BOLD + ConsoleUtil.CYAN
                + "  [*] MAIN MENU" + ConsoleUtil.RESET);
        ConsoleUtil.separator();
        menuItem("1", "Create File");
        menuItem("2", "List All Files");
        menuItem("3", "View File");
        menuItem("4", "Edit File Content");
        menuItem("5", "Rename File");
        menuItem("6", "Move File");
        menuItem("7", "Change Category");
        menuItem("8", "Delete File");
        menuItem("9", "List by Category");
        ConsoleUtil.separator();
        menuItem("0", "Exit");
        ConsoleUtil.separator();
    }

    private void menuItem(String num, String label) {
        System.out.println("  " + ConsoleUtil.GREEN + ConsoleUtil.BOLD
                + "[" + num + "]" + ConsoleUtil.RESET + "  " + label);
    }

    /* ════════════════ 1 – CREATE ══════════════════════════════ */

    private void handleCreate() {
        ConsoleUtil.subHeader("Create New File");

        ConsoleUtil.prompt("File name (e.g. notes.txt)              : ");
        String name = sc.nextLine().trim();

        ConsoleUtil.prompt("Storage path (directory)                : ");
        String path = sc.nextLine().trim();

        ConsoleUtil.prompt("Category (or press Enter for 'General') : ");
        String cat  = sc.nextLine().trim();

        ConsoleUtil.info("Enter file content  (type END on a new line to finish):");
        String content = readMultiLine();

        try {
            FileRecord rec = service.createFile(name, content, path, cat);
            ConsoleUtil.success("File created successfully!");
            ConsoleUtil.printDetail(rec);
        } catch (DuplicateFileException | FileSystemException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 2 – LIST ALL ════════════════════════════ */

    private void handleListAll() {
        ConsoleUtil.subHeader("All Files");
        List<FileRecord> all = service.readAll();
        ConsoleUtil.printTable(all);
        ConsoleUtil.info("Total files: " + all.size());
    }

    /* ════════════════ 3 – VIEW ════════════════════════════════ */

    private void handleView() {
        ConsoleUtil.subHeader("View File");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            // pull live content from disk
            String live = service.readContentFromDisk(rec);
            rec.setContent(live);
            ConsoleUtil.printDetail(rec);
        } catch (FileNotFoundException | FileSystemException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 4 – EDIT CONTENT ════════════════════════ */

    private void handleEditContent() {
        ConsoleUtil.subHeader("Edit File Content");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            ConsoleUtil.info("Current content:");
            ConsoleUtil.separator();
            System.out.println("    " + rec.getContent().replace("\n", "\n    "));
            ConsoleUtil.separator();

            ConsoleUtil.info("Enter NEW content (type END on a new line to finish):");
            String newContent = readMultiLine();

            service.updateContent(rec.getId(), newContent);
            ConsoleUtil.success("Content updated successfully!");
        } catch (FileNotFoundException | FileSystemException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 5 – RENAME ══════════════════════════════ */

    private void handleRename() {
        ConsoleUtil.subHeader("Rename File");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            ConsoleUtil.info("Current name: " + rec.getFileName());

            ConsoleUtil.prompt("New file name: ");
            String newName = sc.nextLine().trim();

            service.renameFile(rec.getId(), newName);
            ConsoleUtil.success("File renamed to: " + newName);
        } catch (FileNotFoundException | FileSystemException | DuplicateFileException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 6 – MOVE ════════════════════════════════ */

    private void handleMove() {
        ConsoleUtil.subHeader("Move File");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            ConsoleUtil.info("Current path: " + rec.getStoragePath());

            ConsoleUtil.prompt("New storage path (directory): ");
            String newPath = sc.nextLine().trim();

            service.moveFile(rec.getId(), newPath);
            ConsoleUtil.success("File moved to: " + newPath);
        } catch (FileNotFoundException | FileSystemException | DuplicateFileException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 7 – CHANGE CATEGORY ════════════════════ */

    private void handleChangeCategory() {
        ConsoleUtil.subHeader("Change Category");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            ConsoleUtil.info("Current category: " + rec.getCategory());

            ConsoleUtil.prompt("New category: ");
            String newCat = sc.nextLine().trim();

            service.updateCategory(rec.getId(), newCat);
            ConsoleUtil.success("Category updated to: " + newCat);
        } catch (FileNotFoundException | FileSystemException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 8 – DELETE ═══════════════════════════════ */

    private void handleDelete() {
        ConsoleUtil.subHeader("Delete File");
        ConsoleUtil.prompt("Enter file ID (first 8 chars) or file name: ");
        String input = sc.nextLine().trim();

        try {
            FileRecord rec = resolveFile(input);
            ConsoleUtil.warning("You are about to DELETE: " + rec.getFileName()
                    + "  at  " + rec.getStoragePath());
            ConsoleUtil.prompt("Confirm? (yes / no): ");
            String confirm = sc.nextLine().trim().toLowerCase();

            if ("yes".equals(confirm) || "y".equals(confirm)) {
                service.deleteFile(rec.getId());
                ConsoleUtil.success("File deleted successfully.");
            } else {
                ConsoleUtil.info("Deletion cancelled.");
            }
        } catch (FileNotFoundException | FileSystemException e) {
            ConsoleUtil.error(e.getMessage());
        }
    }

    /* ════════════════ 9 – LIST BY CATEGORY ════════════════════ */

    private void handleListByCategory() {
        ConsoleUtil.subHeader("List by Category");

        // collect distinct categories
        List<FileRecord> all = service.readAll();
        Set<String>  catSet  = new LinkedHashSet<>();
        for (FileRecord r : all) catSet.add(r.getCategory());
        List<String> categories = new ArrayList<>(catSet);
        Collections.sort(categories);

        if (categories.isEmpty()) {
            ConsoleUtil.warning("No categories exist yet.");
            return;
        }

        ConsoleUtil.info("Available categories:");
        for (String c : categories)
            System.out.println("      * " + c);

        ConsoleUtil.prompt("Enter category name: ");
        String cat = sc.nextLine().trim();

        List<FileRecord> results = service.readByCategory(cat);
        ConsoleUtil.printTable(results);
    }

    /* ════════════════ SHARED HELPERS ══════════════════════════ */

    /** Try ID first, fall back to file-name lookup. */
    private FileRecord resolveFile(String input) throws FileNotFoundException {
        try {
            return service.readById(input);
        } catch (FileNotFoundException e) {
            return service.readByFileName(input);
        }
    }

    /** Read lines until user types END alone. */
    private String readMultiLine() {
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if ("END".equalsIgnoreCase(line.trim())) break;
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }
}
