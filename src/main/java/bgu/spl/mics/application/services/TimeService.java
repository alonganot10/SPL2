package bgu.spl.mics.application.services;

import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.messages.TickBroadcast;

import java.util.concurrent.TimeUnit;

/**
 * TimeService acts as the global timer for the system, broadcasting TickBroadcast messages
 * at regular intervals and controlling the simulation's duration.
 */
public class TimeService extends MicroService {

    private final int TickTime;
    private final int Duration;
    private int currentTick;

    /**
     * Constructor for TimeService.
     *
     * @param TickTime  The duration of each tick in seconds.
     * @param Duration  The total number of ticks before the service terminates.
     */
    public TimeService(int TickTime, int Duration) {
        super("TimeService");
        this.TickTime = TickTime;
        this.Duration = Duration;
        this.currentTick = 0;
    }

    /**
     * Initializes the TimeService.
     * Starts broadcasting TickBroadcast messages and terminates after the specified duration.
     */
    @Override
    protected void initialize() {
        //long terminateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TickTime) * Duration;
        try {
            while (currentTick < Duration) {
                this.wait(TimeUnit.SECONDS.toMillis(TickTime));
                sendBroadcast(new TickBroadcast(++currentTick));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        terminate();
    }
}