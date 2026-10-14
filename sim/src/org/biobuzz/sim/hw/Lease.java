package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl;

// The hardware devices of one Robot Controller run. After a restart the old devices are dead (an OpMode thread
// left over from the old app can no longer move anything), and while the stuck-OpMode monitor force-stops a
// thread every hardware call throws, like the real hub's "throw on network lock acquisition".
public final class Lease {
    public volatile boolean revoked, throwing;

    public void check() {
        if (revoked || throwing) throw new OpModeManagerImpl.ForceStopException();
    }
}
