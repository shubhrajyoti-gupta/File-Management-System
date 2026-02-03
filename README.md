# 📂 File Management System (FMS)

A **console-based file management application** built entirely in **Core Java**. No external libraries, no build tools — only `java.io.*`, `java.util.*`, and `java.text.*`.

Create, read, update, delete, rename, move, and categorise text files from a single interactive terminal menu. All metadata persists across sessions via a local registry file.

---

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack & Constraints](#tech-stack--constraints)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [Class Breakdown](#class-breakdown)
  - [App.java](#appjava--entry-point)
  - [FileRecord.java](#filerecordjava--data-model)
  - [FileRepository.java](#filerepositoryijava--persistence)
  - [FileService.java](#fileservicejava--business-logic)
  - [MainMenu.java](#mainmenu-java--ui-layer)
  - [ConsoleUtil.java](#consoleutiijava--terminal-helpers)
  - [Exceptions](#exceptions)
- [Registry File Format](#registry-file-format)
- [How to Compile](#how-to-compile)
- [How to Run](#how-to-run)
- [Menu Reference](#menu-reference)
- [How Lookups Work](#how-lookups-work)
- [Error Handling Strategy](#error-handling-strategy)
- [Data Flow Diagrams](#data-flow-diagrams)

---

## ✅ Features

| Feature | Details |
|---|---|
| **Create** | Write a new text file to any directory you specify |
| **Read** | View live file content pulled directly off disk |
| **Update** | Overwrite file content in-place |
| **Delete** | Remove the file from disk *and* the registry (with confirmation) |
| **Rename** | Rename a file on disk via `File.renameTo()` |
| **Move** | Relocate a file to a different directory |
| **Categorise** | Tag every file with a custom category; filter the list by it |
| **Persistence** | A local `.dat` registry survives app restarts |
| **Duplicate Guard** | Refuses to create or rename over a file that already exists |
| **Input Validation** | Rejects empty names, illegal OS characters (`< > : " / \ \| ? *`), and names with no extension |
| **Flexible Lookup** | Find any file by its **short 8-char ID** or its **exact file name** |
| **Coloured UI** | ANSI-coloured banners, tables, and status messages (Windows 10+, macOS, Linux) |

---

## ⚙️ Tech Stack & Constraints

| Allowed | Not Used |
|---|---|
| `java.io.*` — `File`, `FileReader`, `FileWriter`, `BufferedReader`, `BufferedWriter`, `Serializable` | `java.nio` (no `Path`, no `Files`) |
| `java.util.*` — `Date`, `UUID`, `Scanner`, `LinkedHashMap`, `ArrayList`, `Collections`, `Comparator`, `Arrays`, `Set`, `LinkedHashSet` | No Maven / Gradle / build tools |
| `java.text.*` — `SimpleDateFormat`, `ParseException` | No external libraries |

---

## 📁 Project Structure

```
src/
└── fms/
    ├── App.java                          ← main() entry point
    ├── model/
    │   └── FileRecord.java               ← data model (id, name, content, path, category, timestamps)
    ├── exception/
    │   ├── FileSystemException.java      ← I/O-level errors (read / write / move failures)
    │   ├── FileNotFoundException.java    ← record not found by ID or name
    │   └── DuplicateFileException.java   ← file with same name already exists at that path
    ├── repository/
    │   └── FileRepository.java           ← in-memory store + pipe-delimited .dat persistence
    ├── service/
    │   └── FileService.java              ← validation, disk I/O coordination, CRUD logic
    ├── util/
    │   └── ConsoleUtil.java              ← ANSI colours, tables, banners, prompts
    └── ui/
        └── MainMenu.java                 ← interactive 10-option console menu
```

---

## 🏗️ Architecture Overview

The project is a classic **three-layer architecture** — UI calls Service, Service calls Repository. Disk I/O for the *actual user files* lives in the Service layer; disk I/O for the *registry* lives in the Repository layer.

```
┌──────────────┐     calls     ┌──────────────┐     calls     ┌────────────────┐
│   MainMenu   │ ─────────────▶│  FileService │ ─────────────▶│  FileRepository│
│  (UI layer)  │               │   (Logic)    │               │  (Persistence) │
└──────────────┘               └──────┬───────┘               └───────┬────────┘
                                      │                               │
                          FileWriter / FileReader           FileWriter / FileReader
                          File.renameTo / File.delete       (fms_registry.dat)
                                      │                               │
                                      ▼                               ▼
                              ┌───────────────┐            ┌─────────────────┐
                              │  User's files │            │  Registry file  │
                              │  (any path)   │            │  ~/.fms_data/   │
                              └───────────────┘            └─────────────────┘
```

---

## 📖 Class Breakdown

---

### `App.java` — Entry Point

**Package:** `fms`

The only class with a `main()` method. It does exactly three things:

1. Builds the path to the registry directory (`~/.fms_data/`), using `java.io.File.separator` so it works on any OS.
2. Constructs the object graph: `FileRepository` → `FileService` → `MainMenu`.
3. Calls `menu.run()` to hand off control to the interactive loop.

If the repository constructor throws an `IOException` (e.g. it can't create `.fms_data`), the error is printed and the JVM exits with code `1`.

```java
// wiring order — each layer needs the one below it
FileRepository repo    = new FileRepository(registryDir);
FileService    service = new FileService(repo);
MainMenu       menu    = new MainMenu(service);
menu.run();
```

---

### `FileRecord.java` — Data Model

**Package:** `fms.model`  
**Implements:** `Serializable`

Every file the system tracks is represented as one `FileRecord`. It holds:

| Field | Type | Description |
|---|---|---|
| `id` | `String` | UUID generated once at creation; never changes |
| `fileName` | `String` | e.g. `notes.txt` |
| `content` | `String` | Full text body of the file |
| `storagePath` | `String` | The directory on disk (no trailing separator) |
| `category` | `String` | User-defined tag; defaults to `"General"` if blank |
| `createdAt` | `Date` | Timestamp set once at construction |
| `updatedAt` | `Date` | Bumped to `new Date()` by every setter |

**Two constructors:**

| Constructor | Used when |
|---|---|
| `FileRecord(fileName, content, storagePath, category)` | Creating a brand-new file — generates a fresh UUID and sets both timestamps to now |
| `FileRecord(id, fileName, content, storagePath, category, createdAt, updatedAt)` | Reconstructing a record that was loaded from the registry file |

**Key method — `toFile()`:**
```java
public File toFile() {
    return new File(storagePath, fileName);   // the actual java.io.File on disk
}
```
Every other class that needs the physical file calls this instead of building a path manually.

---

### `FileRepository.java` — Persistence

**Package:** `fms.repository`

Owns two things:

1. A `LinkedHashMap<String, FileRecord>` that is the live, in-memory store (preserves insertion order).
2. A `File` object pointing to `fms_registry.dat` on disk.

#### CRUD methods

| Method | What it does |
|---|---|
| `save(record)` | Puts into map → rewrites `.dat` |
| `findById(id)` | Direct `map.get()` — O(1) |
| `findByFileName(name)` | Iterates values, case-insensitive match, returns first hit |
| `findAll()` | Returns a new list sorted by `createdAt` descending |
| `findByCategory(cat)` | Filters + sorts same as `findAll` |
| `update(record)` | Same as save — replaces value in map → rewrites `.dat` |
| `delete(id)` | Removes from map → rewrites `.dat` |

#### How persistence works

**Writing (`persistToDisk`):**
```
1.  Serialize every record in the map into one text line each.
2.  Write all lines to a TEMPORARY file (fms_registry.tmp).
3.  Delete the real registry file (if it exists).
4.  Rename .tmp  →  fms_registry.dat
```
The temp-file-then-rename pattern means a crash mid-write cannot corrupt the registry — the old file stays intact until the new one is fully written.

**Reading (`loadFromDisk`):**
```
1.  If fms_registry.dat does not exist → do nothing (first run).
2.  Open with FileReader → BufferedReader.
3.  Read line by line, parse each line back into a FileRecord, put into the map.
```

---

### `FileService.java` — Business Logic

**Package:** `fms.service`

The only class that touches the *user's actual files* on disk. Every operation follows a strict order: **validate → check preconditions → do disk I/O → update registry**. If the registry update fails after the disk operation succeeded, the exception message says so explicitly.

#### CREATE

```
validateFileName(name)                    ← rejects illegal chars, missing extension
new File(storagePath).mkdirs()            ← create directory tree if needed
if target.exists() → DuplicateFileException
writeToFile(target, content)              ← FileWriter + BufferedWriter
repo.save(new FileRecord(...))            ← persist metadata
```

#### READ

| Method | Behaviour |
|---|---|
| `readById(id)` | Tries exact match first, then prefix-search (supports short 8-char IDs) |
| `readByFileName(name)` | Delegates to `repo.findByFileName` |
| `readContentFromDisk(record)` | Opens the file with `FileReader` and reads the *live* bytes — ignores whatever is cached in memory |

#### UPDATE

| Method | Disk action | Registry action |
|---|---|---|
| `updateContent(id, newContent)` | `FileWriter` overwrites the file | `record.setContent()` → `repo.update()` |
| `renameFile(id, newName)` | `oldFile.renameTo(newFile)` | `record.setFileName()` → `repo.update()` |
| `moveFile(id, newPath)` | `mkdirs()` on new dir, then `renameTo()` | `record.setStoragePath()` → `repo.update()` |
| `updateCategory(id, newCat)` | *none* — metadata only | `record.setCategory()` → `repo.update()` |

#### DELETE

```
record = readById(id)
if file exists on disk → file.delete()     ← returns false if it fails → exception
repo.delete(record.getId())                ← remove from registry
```

#### Validation (`validateFileName`)

Rejects:
- `null` or blank names
- Any character in the set `< > : " / \ | ? *`
- Names with no `.` (must have an extension)

#### Private I/O helpers

Both helpers manually close streams in `finally` blocks (no try-with-resources needed for classic `java.io`):

```java
writeToFile(File, String)     // FileWriter → BufferedWriter → flush → close
readFromFile(File)            // FileReader → BufferedReader → readLine loop → close
```

---

### `MainMenu.java` — UI Layer

**Package:** `fms.ui`

Runs an infinite `while (running)` loop. Each iteration prints the menu, reads one line from `Scanner`, and dispatches to a private `handle*()` method via a `switch`.

#### Menu dispatch table

| Input | Method | What it prompts for |
|---|---|---|
| `1` | `handleCreate()` | file name, directory, category, multi-line content |
| `2` | `handleListAll()` | — (prints table directly) |
| `3` | `handleView()` | file ID or name |
| `4` | `handleEditContent()` | file ID or name, then new multi-line content |
| `5` | `handleRename()` | file ID or name, then new name |
| `6` | `handleMove()` | file ID or name, then new directory |
| `7` | `handleChangeCategory()` | file ID or name, then new category |
| `8` | `handleDelete()` | file ID or name, then `yes`/`no` confirmation |
| `9` | `handleListByCategory()` | prints available categories, then one to filter by |
| `0` | — | prints goodbye banner, sets `running = false` |

#### Two shared helpers

**`resolveFile(input)`** — tries `readById` first; if that throws `FileNotFoundException`, falls back to `readByFileName`. This is what makes the short-ID / name duality transparent to every menu handler.

**`readMultiLine()`** — reads lines from Scanner and appends them (joined by `\n`) until the user types `END` alone on a line. Used for create and edit.

---

### `ConsoleUtil.java` — Terminal Helpers

**Package:** `fms.util`  
**Final class, private constructor** — pure static utility, never instantiated.

#### ANSI colour constants

```java
RESET, RED, GREEN, YELLOW, CYAN, BOLD, DIM
```

#### Output methods

| Method | Example output |
|---|---|
| `banner(title)` | Box-drawn double-line border around centred title |
| `subHeader(text)` | Yellow bold `── text ──` |
| `separator()` | Dim horizontal line (66 `─` chars) |
| `success(msg)` | Green `[OK]  msg` |
| `error(msg)` | Red `[!!]  msg` |
| `info(msg)` | Cyan `[i]   msg` |
| `warning(msg)` | Yellow `[!]   msg` |
| `prompt(label)` | Yellow label, **no newline** — cursor stays on the same line |
| `printTable(list)` | Coloured, column-aligned table of FileRecords |
| `printDetail(record)` | Full detail card: every field + indented content block |

#### String helpers (private)

| Helper | Purpose |
|---|---|
| `repeat(char, n)` | Builds a string of `n` copies of one character (uses `Arrays.fill`) |
| `pad(s, width)` | Left-aligns a string inside a fixed-width column |
| `truncate(s, max)` | Cuts long strings and appends `...` |

---

### Exceptions

**Package:** `fms.exception` — all three are **checked** exceptions.

| Class | Thrown when |
|---|---|
| `FileSystemException` | Any `File` / `FileWriter` / `FileReader` operation fails — wraps the original `IOException` as the cause |
| `FileNotFoundException` | `readById` or `readByFileName` finds no matching record |
| `DuplicateFileException` | A create or rename would collide with an existing file on disk |

---

## 📄 Registry File Format

The registry is a plain text file (`fms_registry.dat`). One line per file, seven fields separated by `|`:

```
id | fileName | storagePath | category | createdAt | updatedAt | content
```

**Example:**
```
a1b2c3d4-e5f6-7890-abcd-ef1234567890|notes.txt|/home/user/docs|Work|2026-02-03T14:05:00|2026-02-03T14:10:00|Hello world\nSecond line
```

**Escaping rules (applied to the `content` field):**

| Real character | Stored as |
|---|---|
| `\` (backslash) | `\\` |
| newline | `\n` (literal two chars) |
| carriage return | `\r` (literal two chars) |
| `\|` (pipe) | `\p` (literal two chars) |

The content field is always **last**, and the line is split on only the **first 6 pipes**, so any unescaped pipes that slip through inside content cannot break parsing.

**Registry location:**

| OS | Path |
|---|---|
| Linux / macOS | `~/.fms_data/fms_registry.dat` |
| Windows | `%USERPROFILE%\.fms_data\fms_registry.dat` |

---

## 🔨 How to Compile

From the project root (the folder that contains `src/`):

```bash
javac -d out \
  src/fms/*.java \
  src/fms/model/*.java \
  src/fms/exception/*.java \
  src/fms/repository/*.java \
  src/fms/service/*.java \
  src/fms/util/*.java \
  src/fms/ui/*.java
```

All `.class` files land in the `out/` directory, mirroring the package structure.

---

## ▶️ How to Run

```bash
java -cp out fms.App
```

That is it. No arguments, no config files, no environment variables.

---

## 🗺️ Menu Reference

```
  ──────────────────────────────────────────────────
  [*] MAIN MENU
  ──────────────────────────────────────────────────
  [1]  Create File
  [2]  List All Files
  [3]  View File
  [4]  Edit File Content
  [5]  Rename File
  [6]  Move File
  [7]  Change Category
  [8]  Delete File
  [9]  List by Category
  ──────────────────────────────────────────────────
  [0]  Exit
  ──────────────────────────────────────────────────
```

### Quick walkthrough — creating and editing a file

```
[1]  Create File
  File name (e.g. notes.txt)              : meeting.txt
  Storage path (directory)                : /home/user/work
  Category (or press Enter for 'General') : Meetings
  Enter file content  (type END on a new line to finish):
  Discussed Q1 roadmap.
  Action items assigned.
  END
  [OK]  File created successfully!
```

```
[4]  Edit File Content
  Enter file ID (first 8 chars) or file name: meeting.txt
  Current content:
  ──────────────────────────────────────────────────
      Discussed Q1 roadmap.
      Action items assigned.
  ──────────────────────────────────────────────────
  Enter NEW content (type END on a new line to finish):
  Discussed Q1 roadmap.
  Action items assigned.
  Follow-up scheduled for Friday.
  END
  [OK]  Content updated successfully!
```

---

## 🔍 How Lookups Work

Every menu option that targets a specific file asks for an identifier. The system resolves it in this order:

```
Input received
      │
      ▼
repo.findById(input)          ← exact UUID match
      │
      │  not found?
      ▼
prefix search over findAll()  ← does any UUID start with input?
      │
      │  still not found?
      ▼
repo.findByFileName(input)    ← case-insensitive name match
      │
      │  still not found?
      ▼
throw FileNotFoundException
```

This means all three of the following refer to the same file:

```
a1b2c3d4-e5f6-7890-abcd-ef1234567890    ← full UUID
a1b2c3d4                                 ← first 8 chars
meeting.txt                              ← file name
```

---

## 🛡️ Error Handling Strategy

Every checked exception is **domain-specific** and carries a clear message. The UI layer catches them individually and prints the message via `ConsoleUtil.error()` — the app never crashes to a stack trace under normal conditions.

| Scenario | Exception | Caught in |
|---|---|---|
| Can't create / write / read a file | `FileSystemException` | every `handle*()` method |
| ID or name doesn't match any record | `FileNotFoundException` | every `handle*()` method |
| Create or rename would overwrite | `DuplicateFileException` | `handleCreate`, `handleRename` |
| Registry write fails after disk op succeeds | `FileSystemException` (message explicitly says so) | every `handle*()` method |

---

## 📊 Data Flow Diagrams

### Create File

```
User input
    │  name, path, category, content
    ▼
MainMenu.handleCreate()
    │
    ▼
FileService.createFile()
    ├── validateFileName()             ← rejects bad names
    ├── File.mkdirs()                  ← create directory if missing
    ├── File.exists() check            ← duplicate guard
    ├── writeToFile() ─── FileWriter ──▶ disk (the actual .txt)
    └── repo.save()   ─── FileWriter ──▶ disk (fms_registry.dat)
```

### Delete File

```
User input (id or name) + "yes" confirmation
    │
    ▼
MainMenu.handleDelete()
    │
    ▼
FileService.deleteFile()
    ├── readById()                     ← resolve the record
    ├── File.exists() + File.delete()  ← remove from disk
    └── repo.delete()                  ← remove from registry
```

### App Startup (registry reload)

```
App.main()
    │
    ▼
new FileRepository(registryDir)
    ├── new File(registryDir).mkdirs()          ← ensure ~/.fms_data exists
    └── loadFromDisk()
            ├── if fms_registry.dat missing → skip (first run)
            └── FileReader → BufferedReader → readLine loop
                    └── fromLine() parses each line back into a FileRecord
                            └── store.put(id, record)   ← map is fully populated
```
