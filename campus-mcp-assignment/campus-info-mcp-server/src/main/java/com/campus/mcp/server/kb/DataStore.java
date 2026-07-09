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
                        "# ref | studentId | fromDate | toDate | reason | createdAt\n"); //IGNORE
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
     *  returns the student data in just a string
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
     *  gets students by their id
     * 
     *  returns an arraylist of strings of student attributes
     *  returns null if no student was found
     */
    public synchronized List<String> getStudentDataById(String studentId) {
        List<String> studentDataMap = null;
        for (String line : readDataLines(studentsFile)) {
            String[] parts = line.split("\\s*\\|\\s*");
            if (!parts[0].equalsIgnoreCase(studentId)) continue;
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
    
    
    /**
     * "Deletes" an entry from bookings.txt 
     *  provided that we give the #ref id
     * 
     *  returns an ArrayList of String objects of the data row of the booking that was deleted, including "# ref"
     *  return null if no booking with that bookingId was found
     */
    public synchronized List<String> deleteBooking(String bookingId) {
        //this initial block checks if there is even a booking by bookingId
        // if not, then it just returns null straight away
        List<String> bookingData = null;
        for (String line : readDataLines(bookingsFile)) {
            if (line.contains("# ref")) continue;
            bookingData = (bookingId.equalsIgnoreCase(line.split("\\s*\\|\\s*")[0])) 
                    ? new ArrayList<>(Arrays.asList(line.split("\\s*\\|\\s*"))) : null;
        }
        
        //no such booking id found
        if (bookingData == null) return null;
        
        delete(bookingsFile, bookingId);
        
        return bookingData;
    }
    
    
    /**
     *  Gets all the bookings
     * 
     *  returns an arraylist of map objects (map of attribute to value) for easier indexing later
     *  returns null if no bookings were found
     */
    public synchronized List<Map<String, String>> getAllBookings() {
        List<Map<String,String>> out = new ArrayList<>();
        for (String line : readDataLines(bookingsFile)) {
            if (line.contains("# ref")) continue;
            
            String[] parts = line.split("\\s*\\|\\s*");
            Map<String, String> bookingDataRow = new HashMap<>(Map.of(
                    "booking_id", parts[0],
                    "resource_id", parts[1],
                    "booking_date", parts[2],
                    "booking_start_time", parts[3],
                    "booking_end_time", parts[4],
                    "student_id", parts[5],
                    "created_datetime", parts[6]
            ));
            out.add(bookingDataRow);
        }
        
        out = (out.isEmpty()) ? null : out;
        return out;
    }

    
    
    
    // ---- low-level file helpers -----------------------------------------
    
    /**
     * ONLY DELETES FROM TEXT FILES IN SERVER'S DATA FOLDER, REQUIRES REF ID
     * 
     * Essentially adds "*DELETED*" to the front of the # ref of that row
     * 
     * returns 0 if successfully "deleted" row
     * returns 1 if failed to find matching ref
     */
    //CREATED BY KEITH
    private int delete(Path file, String ref) {
        //getting all the original lines from the target file...
        List<String> lines = readDataLines(file);
        
        //store only the header row (assuming there is only one line)
        String[] dataLines = new String[lines.size() - 1];
        String headerLine = lines.get(0);
        List<String> headerAttributes = new ArrayList<>(Arrays.asList(headerLine.split("\\s*\\|\\s*")));
        
        //store only the data rows (exclude header line) (initialisatino)
        String[][] dataMatrix = new String[lines.size() - 1][headerAttributes.size()];
        for (int row = 1; row < lines.size(); row++) {
            //populating the matrix...
            dataMatrix[row] = lines.get(row).split("\\s*\\|\\s*");
            
            //assuming that we maintained "# ref" as the first field of each and every data text store
            //will commence the "deletion" here, first check dataMatrix[row][0] (# ref) for the correct data row, then append *DELETED* to REF_ID (*DELETED* REF_ID)
            if (dataMatrix[row][0].equalsIgnoreCase(ref)) {
                dataMatrix[row][0] = "*DELETED* " + dataMatrix[row][0];

                //join the columns in the matrix back together with " | "...
                for (String dataLine : dataLines) {
                    dataLine = String.join(" | ", dataMatrix[row]);
                }
                
                //finally, clear the file, and rewrite to it using append(...)
                clear(file);
                append(file, headerLine); //first appending the header...
                for (String dataLine : dataLines) {
                    append(file, dataLine);
                }
                
                return 0; //0 means successfully deleted
            }
        }
        return 1; //1 means not found, don't change anything in the file...
    }
    
    
    
    /**
     *  Appends to the String "line" to the file on a new line
     * 
     */
    private void append(Path file, String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    
    /**
     *  Literally clears all the text from a text file
     * 
     */
    //CREATED BY KEITH
    private void clear(Path file) {
        try {
            Files.write(file, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    
    /**
     *  Returns an arraylist of each line of the file (including the header)
     * 
     */
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
    
    /**
     *  Returns the current number of newlines in the file
     * 
     */
    private int countDataLines(Path file) {
        return readDataLines(file).size();
    }
}
