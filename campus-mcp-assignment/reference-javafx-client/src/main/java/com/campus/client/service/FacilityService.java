package com.campus.client.service;

import com.campus.client.mcp.CampusMcpClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class FacilityService {

    private static final String FACILITIES_RESOURCE_URI = "campus://facilities";

    private final CampusMcpClient mcp;
    private String cachedFacilitiesText;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FacilityService(CampusMcpClient mcp) {
        this.mcp = mcp;
    }

    private synchronized String getFacilitiesTextSync() {
        if (cachedFacilitiesText == null || cachedFacilitiesText.isBlank()) {
            cachedFacilitiesText = mcp.readResource(FACILITIES_RESOURCE_URI);
        }

        return cachedFacilitiesText;
    }

    public Future<List<String>> getFacilityIds() {
        return executor.submit(() -> {
            String text = getFacilitiesTextSync();

            Set<String> ids = new LinkedHashSet<>();
            String[] lines = text.split("\\R");

            for (String line : lines) {
                if (!line.contains("|")) {
                    continue;
                }

                String[] parts = line.split("\\s*\\|\\s*");

                if (parts.length < 6) {
                    continue;
                }

                String roomId = parts[0].trim();

                if (roomId.equalsIgnoreCase("ROOM")) {
                    continue;
                }
                if (!roomId.isBlank()) {
                    ids.add(roomId);
                }
            }
            return new ArrayList<>(ids);
        });
    }


    public Future<String> getFacilityDetails(String facilityId) {
        return executor.submit(() -> {


            if (facilityId == null || facilityId.isBlank()) {
                return "Please choose a facility first.";
            }

            String text = getFacilitiesTextSync();

            String[] lines = text.split("\\R");

            for (String line : lines) {
                if (!line.contains("|")) {
                    continue;
                }

                String[] parts = line.split("\\s*\\|\\s*");

                if (parts.length < 6) {
                    continue;
                }

                String room = parts[0].trim();

                if (room.equalsIgnoreCase("ROOM")) {
                    continue;
                }

                if (room.equalsIgnoreCase(facilityId.trim())) {
                    String type = parts[1].trim();
                    String capacity = parts[2].trim();
                    String building = parts[3].trim();
                    String open = parts[4].trim();
                    String close = parts[5].trim();

                    return "Room: " + room + "\n" + "Type: " + type + "\n" + "Capacity: " + capacity + "\n" + "Building: " + building + "\n" + "Opening Hours: " + open + " - " + close;
                }
            }

            return "No information found for facility: " + facilityId;
        });

        }
        public void shutdown(){
            executor.shutdown();
    }
}
