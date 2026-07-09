package com.campus.mcp.server.tools;

import com.campus.mcp.server.kb.DataStore;
import com.campus.mcp.server.kb.KnowledgeBase;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the campus {@code Tools} exposed to MCP clients. Tools are actions the LLM can choose
 * to invoke: searching the knowledge base (RAG retrieval), checking availability, making a
 * booking, listing lecturer slots, and submitting a leave application.
 */
public final class CampusTools {

    private final KnowledgeBase kb;
    private final DataStore dataStore;
    private final McpJsonMapper jsonMapper;

    public CampusTools(KnowledgeBase kb, DataStore dataStore, McpJsonMapper jsonMapper) {
        this.kb = kb;
        this.dataStore = dataStore;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> all() {
        return List.of(
                searchCampusInfo(),
                checkRoomAvailability(),
                bookResource(),
                listLecturerSlots(),
                submitLeaveApplication(),
                addNewStudent(),
                getStudentDataById());
    }

    // 1. RAG retrieval tool -------------------------------------------------

    private SyncToolSpecification searchCampusInfo() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "Natural-language question about campus services" },
                "topK":  { "type": "integer", "description": "How many passages to return (default 3)" }
              },
              "required": ["query"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("search_campus_info")
                        .description("Retrieve the most relevant passages from the campus knowledge base "
                                + "for a question. Use this to ground answers (the Retrieval step of RAG).")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String query = str(a, "query");
                    int topK = a.get("topK") instanceof Number n ? n.intValue() : 3;
                    List<KnowledgeBase.Hit> hits = kb.retrieve(query, topK);
                    if (hits.isEmpty()) {
                        return text("No relevant passages found for: " + query);
                    }
                    String body = hits.stream()
                            .map(h -> "Source: " + h.source() + "\n" + h.text())
                            .collect(Collectors.joining("\n\n---\n\n"));
                    return text(body);
                })
                .build();
    }

    // 2. Room availability --------------------------------------------------

    private SyncToolSpecification checkRoomAvailability() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "date":     { "type": "string", "description": "Date in yyyy-MM-dd" },
                "building": { "type": "string", "description": "Optional building code, e.g. D, E, LIB, OUT" }
              },
              "required": ["date"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("check_room_availability")
                        .description("List bookable rooms and which ones already have bookings on a given date.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String date = str(a, "date");
                    String building = str(a, "building");
                    List<String[]> rooms = parseRooms();
                    List<String> booked = dataStore.bookingsOn(date).stream()
                            .map(line -> line.split("\\s*\\|\\s*"))
                            .filter(p -> p.length >= 2)
                            .map(p -> p[1])
                            .collect(Collectors.toList());

                    StringBuilder sb = new StringBuilder("Availability on " + date + ":\n");
                    for (String[] r : rooms) {
                        String id = r[0], type = r[1], cap = r[2], bldg = r[3];
                        if (!building.isBlank() && !bldg.equalsIgnoreCase(building)) {
                            continue;
                        }
                        boolean isBooked = booked.contains(id);
                        sb.append(String.format("  %-7s %-16s cap %-3s %s  -> %s%n",
                                id, type, cap, bldg, isBooked ? "HAS BOOKING(S)" : "free"));
                    }
                    return text(sb.toString());
                })
                .build();
    }

    // 3. Book a resource ----------------------------------------------------

    private SyncToolSpecification bookResource() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "resourceId": { "type": "string", "description": "Room/resource id, e.g. KA-P1" },
                "date":       { "type": "string", "description": "Date in yyyy-MM-dd" },
                "startTime":  { "type": "string", "description": "Start time HH:mm" },
                "endTime":    { "type": "string", "description": "End time HH:mm" },
                "studentId":  { "type": "string", "description": "Student id of the requester" }
              },
              "required": ["resourceId", "date", "startTime", "endTime", "studentId"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("book_resource")
                        .description("Create a booking for a bookable campus resource. Returns a booking reference.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String resourceId = str(a, "resourceId");
                    boolean known = parseRooms().stream().anyMatch(r -> r[0].equalsIgnoreCase(resourceId));
                    if (!known) {
                        return error("Unknown resource '" + resourceId + "'. Use check_room_availability first.");
                    }
                    String ref = dataStore.addBooking(resourceId, str(a, "date"),
                            str(a, "startTime"), str(a, "endTime"), str(a, "studentId"));
                    return text("Booking confirmed. Reference " + ref + " for " + resourceId
                            + " on " + str(a, "date") + " " + str(a, "startTime") + "-" + str(a, "endTime") + ".");
                })
                .build();
    }
    
    private SyncToolSpecification cancelBooking() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "booking_id": { "type": "string", "description": "room booking id to delete" }
              },
              "required": ["booking_id"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("cancel_booking")
                        .description("Cancel room bookings, and returns a String of the cancelled booking")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    String bookingId = str(args, "booking_id");
                    List<String> cancelledBookingData = dataStore.deleteBooking(bookingId);
                    
                    //if dataStore.deleteBooking returns a null, means it failed to find any booking with booking_id
                    if (cancelledBookingData == null) return error("No such booking with booking id %s".formatted(bookingId.toUpperCase()));
                    
                    //otherwise, if the dataStore.deleteBooking did work, it'll return an ArrayList of strings of the attributes of the deleted data row
                    //which is parsed as a string with " | " delimiters
                    return text(String.join(" | ", cancelledBookingData));
                    
                })
                .build();
    }
    
    private SyncToolSpecification getRoomBookingByStudentId() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "student_id": { "type": "string", "description": "student id that booked the room(s)" }
              },
              "required": ["student_id"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("get_student_room_bookings")
                        .description("Pass in the target student id, returns an ArrayList of full rows of booking data rows that are linked to that student's id")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    String studentId = str(args, "student_id");
                    
                    //temp store for the return results of dataStore.getAllBookings with an added filter (filter with student_id)
                    List<String> bookingsData = new ArrayList<>();
                    for (Map<String, String> booking : dataStore.getAllBookings()) {
                        if (booking.get("student_id").equalsIgnoreCase(studentId)) {
                            //internally creates a new String[] to be able to use the String.join(delimiter, String[]) method
                            bookingsData.add(String.join(" | ", booking.values().toArray(String[]::new)));
                        }
                    }
                    
                    String fullName = String.join(" ", List.of(
                        dataStore.getStudentDataById(studentId).get(2),
                        dataStore.getStudentDataById(studentId).get(3),
                        dataStore.getStudentDataById(studentId).get(4)
                    ));
                    
                    //validating that bookingsData is empty or not, if it is empty, it means that there are no bookings linked to the student id
                    //if there are bookings, that means the student has made bookings
                    if (bookingsData.isEmpty()) return text("No bookings found under %s, Please make bookings first".formatted(fullName));
                    return text(String.join(" | ", bookingsData));
                    
                })
                .build();
    }

    // 4. Lecturer slots -----------------------------------------------------

    private SyncToolSpecification listLecturerSlots() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "lecturerName": { "type": "string", "description": "Full or partial lecturer name" },
                "day":          { "type": "string", "description": "Optional weekday, e.g. Monday" }
              },
              "required": ["lecturerName"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("list_lecturer_slots")
                        .description("List published consultation slots for a lecturer, optionally filtered by weekday.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String name = str(a, "lecturerName").toLowerCase();
                    String day = str(a, "day").toLowerCase();
                    List<String> matches = new ArrayList<>();
                    for (String line : kb.rawDocument("lecturers.txt").split("\\r?\\n")) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith(":=")) {
                            continue;
                        }
                        String[] p = line.split("\\s*\\|\\s*");
                        if (p.length < 4) {
                            continue;
                        }
                        boolean nameOk = p[0].toLowerCase().contains(name);
                        boolean dayOk = day.isBlank() || p[2].toLowerCase().contains(day);
                        if (nameOk && dayOk) {
                            matches.add(p[0].strip() + " (" + p[1].strip() + ") " + p[2].strip()
                                    + ": " + p[3].strip());
                        }
                    }
                    return matches.isEmpty()
                            ? text("No consultation slots found for '" + str(a, "lecturerName") + "'.")
                            : text(String.join("\n", matches));
                })
                .build();
    }

    // 5. Leave application --------------------------------------------------

    private SyncToolSpecification submitLeaveApplication() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "studentId": { "type": "string" },
                "fromDate":  { "type": "string", "description": "yyyy-MM-dd" },
                "toDate":    { "type": "string", "description": "yyyy-MM-dd" },
                "reason":    { "type": "string" }
              },
              "required": ["studentId", "fromDate", "toDate", "reason"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("submit_leave_application")
                        .description("Submit a student leave application. Returns a leave reference number.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String ref = dataStore.addLeave(str(a, "studentId"), str(a, "fromDate"),
                            str(a, "toDate"), str(a, "reason"));
                    return text("Leave application received. Reference " + ref
                            + ". The programme office will review it within 2 working days.");
                })
                .build();
    }
    
    
    // 6. Student related tools (addNewStudent ("add_new_student"), getStudentDataById ("get_student_data"))
    
    /**
     *  THIS SHOULD HAVE NO ERRORS
     */
    private SyncToolSpecification addNewStudent() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "password": { "type": "string" },
                "fname":  { "type": "string" },
                "mname":    { "type": "string" },
                "lname":    { "type": "string" }
              },
              "required": ["password", "fname"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("add_new_student")
                        .description("Add a student to students.txt. Returns the studentId of the added Student")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    //has sender-message (whatever you execute from the DataStore) and return-message (whatever you wish to return through text(String) )
                    Map<String, Object> args = request.arguments(); //ABSOLUTELY NECESSARY, REQUIRED TO GEt THE ARGUMENTS PASSED BY THE CLIENT TO THE SERVER
                    /**
                     *  The block underneath basically calls the DataStore method defined in DataStore.java
                     * 
                     */
                    String studentId = dataStore.addStudent(
                            str(args, "password"),
                            str(args, "fname"),
                            str(args, "mname"), //str already returns an empty String if this attribute is missing
                            str(args, "lname")  //str already returns an empty String if this attribute is missing
                    );
                    
                    //text MUST take a string
                    return text(studentId);
                    
                }).build();
    }
    
    private SyncToolSpecification getStudentDataById() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string" }
              },
              "required": ["id"]
            }
            """;
        
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("get_student_data")
                        .description("Get a student from the students.txt. Returns the {password, fname, mname, lname}")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    System.out.println(1);
                    Map<String, Object> args = request.arguments();
                    System.out.println(2);
                    List<String> tmpstudentData = dataStore.getStudentDataById(str(args, "id").toUpperCase());
                    String studentData = (tmpstudentData == null) ? 
                            String.format("NO STUDENT OF ID %s FOUND IN students.txt", str(args, "id"))
                            :
                            String.join(" | ", tmpstudentData);
                    System.out.println(3);
                    return text(studentData);
                })
                .build();
    }
    
    // 7. 

    // ---- helpers ----------------------------------------------------------

    /** Parses the room table from facilities.txt into [id, type, capacity, building] rows. */
    private List<String[]> parseRooms() {
        List<String[]> rooms = new ArrayList<>();
        for (String line : kb.rawDocument("facilities.txt").split("\\r?\\n")) {
            String[] p = Arrays.stream(line.split("\\s*\\|\\s*")).map(String::strip).toArray(String[]::new);
            // Room rows look like: HC-01 | discussion_room | 6 | HC | 08:00 | 21:00
            if (p.length >= 4 && p[0].matches("[A-Z]{2}-[A-Z0-9]+") && p[2].matches("\\d+")) {
                rooms.add(new String[]{p[0], p[1], p[2], p[3]});
            }
        }
        return rooms;
    }

    private static CallToolResult text(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).isError(true).build();
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : v.toString();
    }
}
