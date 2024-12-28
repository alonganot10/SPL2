package bgu.spl.mics.application.objects;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Set;

/**
 * Represents an object tracked by the LiDAR.
 * This object includes information about the tracked object's ID, description, 
 * time of tracking, and coordinates in the environment.
 */
public class TrackedObject {
    private final String id;
    private final int time;
    private final String description;
    private final Array coordinates; //Maybe need to be changed to List<CloudPoint>

    public TrackedObject(String id, int time, String description, Array coordinates) {
        this.id = id;
        this.time = time;
        this.description = description;
        this.coordinates = coordinates;
    }
}
