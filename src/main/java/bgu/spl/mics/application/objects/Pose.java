package bgu.spl.mics.application.objects;

/**
 * Represents the robot's pose (position and orientation) in the environment.
 * Includes x, y coordinates and the yaw angle relative to a global coordinate system.
 */
public class Pose {
    private double x;
    private double y;
    private double yaw; // The angle of the robot to the axis of the charging station
    private int time; // the time the robot reaches to this position

    public Pose(float x, float y, float yaw, int time) {
        this.x = x;
        this.y = y;
        this.yaw = yaw;
        this.time = time;
    }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getYaw() { return yaw; }

    public int getTime() { return time; }
}
