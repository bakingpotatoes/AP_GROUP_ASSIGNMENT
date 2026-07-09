package com.campus.mcp.server.kb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * Persists mutable campus data (bookings, leave applications) to plain text files in a
 * {@code data/} directory next to the running server. There is deliberately <b>no database</b>:
 * the assignment requires file-based storage, and this class shows one clean way to do it.
 *
 * <p>Thread-safety: each write is synchronized and uses an append open-option, which is
 * sufficient for a teaching server handling a handful of concurrent clients.</p>
 */
public final class DataStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Path dataDir;
    private final Path bookingsFile;
    private final Path leaveFile;
    private final Path studentsFile;
    private final AtomicInteger bookingSeq = new AtomicInteger(0);
    private final AtomicInteger leaveSeq = new AtomicInteger(0);
    private final AtomicInteger studentsSeq = new AtomicInteger(0);
    
    public DataStore(Path dataDir) {
        this.dataDir = dataDir;
        this.bookingsFile = dataDir.resolve("bookings.txt");
        this.leaveFile = dataDir.resolve("leave_applications.txt");
        this.studentsFile = dataDir.resolve("students.txt"); //the students' login information
        init();
    }

    private void init() {
        try {
            Files.createDirectories(dataDir);
            /**
             *  Create your file-data structure here
             *  
             **/
            if (Files.notExists(bookingsFile)) {
                Files.writeString(bookingsFile,
                        "# ref | resourceId | date | start | end | studentId | createdAt\n");
            }
            if (Files.notExists(leaveFile)) {
                Files.writeString(leaveFile,
                        "# ref | studentId | fromDate | toDate | reason | createdAt\n");
            }
            if(Files.notExists(studentsFile)) {
                Files.writeString(studentsFile,
                        "# ref | studentPassword | studentFName | studentMName | studentLName\n");
            }
            
            // Seed sequence numbers from existing line counts so refs stay unique across restarts.
            bookingSeq.set(countDataLines(bookingsFile));
            leaveSeq.set(countDataLines(leaveFile));
            studentsSeq.set(countDataLines(studentsFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Records a resource booking and returns its reference number. */
    public synchronized String addBooking(String resourceId, String date, String start,
                                          String end, String studentId) {
        String ref = "BK-" + (1000 + bookingSeq.incrementAndGet());
        String line = String.join(" | ", ref, resourceId, date, start, end, studentId,
                LocalDateTime.now().format(TS));
        append(bookingsFile, line);
        return ref;
    }

    /** @return existing bookings for a given date (used to compute availability). */
    public synchronized List<String> bookingsOn(String date) {
        List<String> out = new ArrayList<>();
        for (String line : readDataLines(bookingsFile)) {
            String[] parts = line.split("\\s*\\|\\s*");
            if (parts.length >= 3 && parts[2].equals(date)) {
                out.add(line);
            }
        }
        return out;
    }

    /** Records a leave application and returns its reference number. */
    public synchronized String addLeave(String studentId, String fromDate, String toDate, String reason) {
        String ref = "LV-" + (5000 + leaveSeq.incrementAndGet());
        String safeReason = reason == null ? "" : reason.replace("|", "/").replace("\n", " ");
        String line = String.join(" | ", ref, studentId, fromDate, toDate, safeReason,
                LocalDateTime.now().format(TS));
        append(leaveFile, line);
        return ref;
    }
    
    
    /**
     *  There should be no such thing as a "duplicate" student (student data with duplicate studentids), as AtomicInteger will deal with the student IDs for us
     * 
     */
    public synchronized String addStudent(String studentPassword, String studentFName, String studentMName, String studentLName) {
        String studentId = "S-" + (studentsSeq.incrementAndGet());
        String dataLine = String.join(" | ", studentId, studentPassword, studentFName, studentMName, studentLName);
        append(studentsFile, dataLine);
        return studentId;
        
    }
    
    /**
     *  returns an arraylist of student id Strings
     *  (ARRAYLIST CAN BE EMPTY)
     * 
     */
//    public synchronized List<String> getStudentIds() {
//        List<String> studentIds = new ArrayList<>();
//        for (String line : readDataLines(studentsFile)) { //readDataLines returns the 
//            String[] parts = line.split("\\s*\\|\\s*"); //stores the data separated into "parts" by " | " delimiters
//            studentIds.add(parts[0]);
//        }
//        return studentIds;
//    }
    
    /**
     * returns a map of the entire row of that studentId
     * (MAP CANNOT BE EMPTY)
     */
    public synchronized List<String> getStudentDataById(String studentId) {
        List<String> studentDataMap = null;
        for (String line : readDataLines(studentsFile)) {
            String[] parts = line.split("\\s*\\|\\s*");
            if (parts[0].equalsIgnoreCase(studentId)) continue;
            //there should only be one instance of a student data row with the studentId
            //mapping here
            studentDataMap = new ArrayList<>(List.of(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4]
                    
            ));
        }
        
        
        return studentDataMap; //if the map is empty, that means the studentId row in students.txt couldn't be found, hence, a null is returned
    }

    // ---- low-level file helpers -----------------------------------------
    
    /**
     * ONLY DELETES FROM TEXT FILES IN SERVER'S DATA FOLDER, REQUIRES REF ID
     * 
     * Essentially just replaces that row with * | * | * | *
     * 
     * 
     */
    private void delete(Path file, String ref) {
        List<String> lines = readDataLines(file);
        //store only the header row (assuming there is only one line)
        String headerLine = lines.get(0);
        List<String> headerAttributes = new ArrayList<>(Arrays.asList(headerLine.split("\\s*\\|\\s*")));
        
        //store only the data rows (exclude header line) (initialisatino)
        String[][] dataMatrix = new String[lines.size() - 1][headerAttributes.size()];
        for (int row = 1; row < lines.size(); row++) {
            String[] tmpDataFields = lines.get(row).split("\\s*\\|\\s*");
            //assuming that we maintained "# ref" as the first field of each and every data text store
            //will commence the "deletion" here, first check row[0] (# ref) for the correct data row, then append *DELETED* to REF_ID (*DELETED* REF_ID)
            
        }
    }
    
    private void append(Path file, String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> readDataLines(Path file) {
        try {
            List<String> lines = new ArrayList<>();
            for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (l.toLowerCase().contains("*deleted*")) continue; //ignores deleted data
                if (!l.isBlank() && !l.startsWith("#")) {
                    lines.add(l);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private int countDataLines(Path file) {
        return readDataLines(file).size();
    }
}
