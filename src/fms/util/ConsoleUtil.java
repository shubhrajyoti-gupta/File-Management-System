package fms.util;

import fms.model.FileRecord;

import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Static helpers for coloured, formatted terminal output.
 * ANSI codes work on Windows 10+, macOS, Linux out of the box.
 *
 * Imports: java.util.*   java.text.*
 */
public final class ConsoleUtil {

    /* ── ANSI codes ──────────────────────────────────────────── */
    public static final String RESET  = "\033[0m";
    public static final String RED    = "\033[31m";
    public static final String GREEN  = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String CYAN   = "\033[36m";
    public static final String BOLD   = "\033[1m";
    public static final String DIM    = "\033[2m";

    private static final SimpleDateFormat DT = new SimpleDateFormat("dd-MMM-yyyy HH:mm");

    private ConsoleUtil() {}  // utility class – never instantiate

    /* ── banners & separators ────────────────────────────────── */

    public static void banner(String title) {
        int w = 70;
        System.out.println();
        System.out.println(CYAN + BOLD + top(w) + RESET);
        int pad = (w - 2 - title.length()) / 2;
        int right = w - 2 - pad - title.length();
        System.out.println(CYAN + BOLD + "\u2551" + spaces(pad) + title + spaces(right) + "\u2551" + RESET);
        System.out.println(CYAN + BOLD + bot(w) + RESET);
    }

    public static void subHeader(String text) {
        System.out.println(YELLOW + BOLD + "\n  ── " + text + " ──" + RESET);
    }

    public static void separator() {
        System.out.println(DIM + "  " + repeat('\u2500', 66) + RESET);
    }

    /* ── status messages ─────────────────────────────────────── */

    public static void success(String msg) {
        System.out.println(GREEN + BOLD + "  [OK]  " + msg + RESET);
    }
    public static void error(String msg) {
        System.out.println(RED   + BOLD + "  [!!]  " + msg + RESET);
    }
    public static void info(String msg) {
        System.out.println(CYAN + "  [i]   " + msg + RESET);
    }
    public static void warning(String msg) {
        System.out.println(YELLOW + "  [!]   " + msg + RESET);
    }

    /* ── table ───────────────────────────────────────────────── */

    public static void printTable(List<FileRecord> records) {
        if (records.isEmpty()) {
            warning("No files found.");
            return;
        }

        // column widths
        int idW = 10, nameW = 28, catW = 14, dateW = 18, pathW = 30;

        System.out.println();
        // header
        System.out.println(BOLD + CYAN +
                pad("ID",       idW)  +
                pad("File Name", nameW) +
                pad("Category", catW)  +
                pad("Created",  dateW) +
                pad("Path",     pathW) + RESET);
        System.out.println(DIM + "  " + repeat('\u2500', idW + nameW + catW + dateW + pathW + 4) + RESET);

        // rows
        for (FileRecord r : records) {
            System.out.println(
                pad(r.getShortId(),                          idW)  +
                GREEN + pad(truncate(r.getFileName(), nameW - 2),  nameW) + RESET +
                pad(truncate(r.getCategory(), catW - 2),           catW)  +
                DIM  + pad(DT.format(r.getCreatedAt()),            dateW) +
                       pad(truncate(r.getStoragePath(), pathW - 2), pathW) + RESET);
        }
        System.out.println();
    }

    /* ── detail card ─────────────────────────────────────────── */

    public static void printDetail(FileRecord r) {
        System.out.println();
        subHeader("File Details");
        System.out.println(BOLD + "  ID         : " + RESET + r.getId());
        System.out.println(BOLD + "  File Name  : " + RESET + GREEN + r.getFileName() + RESET);
        System.out.println(BOLD + "  Category   : " + RESET + r.getCategory());
        System.out.println(BOLD + "  Path       : " + RESET + r.getStoragePath());
        System.out.println(BOLD + "  Created    : " + RESET + DT.format(r.getCreatedAt()));
        System.out.println(BOLD + "  Updated    : " + RESET + DT.format(r.getUpdatedAt()));
        System.out.println(BOLD + "  Content    :" + RESET);
        separator();
        String[] lines = r.getContent().split("\n", -1);
        for (String line : lines) {
            System.out.println("    " + line);
        }
        separator();
    }

    /* ── input prompt ────────────────────────────────────────── */

    public static void prompt(String label) {
        System.out.print(YELLOW + "  " + label + RESET);
    }

    /* ── private string helpers ──────────────────────────────── */

    private static String top(int w) {
        return "\u2554" + repeat('\u2550', w - 2) + "\u2557";
    }
    private static String bot(int w) {
        return "\u255a" + repeat('\u2550', w - 2) + "\u255d";
    }
    private static String spaces(int n) {
        return repeat(' ', n);
    }

    /** Repeat a single character n times. */
    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        char[] arr = new char[n];
        Arrays.fill(arr, c);
        return new String(arr);
    }

    /** Left-pad a string to exactly 'width' characters (with leading 2-space indent on first call). */
    private static String pad(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return "  " + s + spaces(width - s.length() - 2);
    }

    private static String truncate(String s, int max) {
        if (s == null)          return "";
        if (s.length() <= max)  return s;
        return s.substring(0, max - 3) + "...";
    }
}
