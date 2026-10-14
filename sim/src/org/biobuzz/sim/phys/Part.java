package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import java.util.Map;

// One physical subsystem of the robot (drive, turret, flywheels, ...). Stepped by the physics thread under the
// world lock; state() feeds the Workbench page; hand() is a student's hands-on action from the Workbench.
public interface Part {
    String id();

    void step(double dt, World w);

    void state(Map<String, Object> out);

    default boolean hand(String action, JsonObject args, World w) {
        return false;
    }

    // the true answer for every results-file key this part's setup program measures (report card)
    default void expected(Map<String, Expected> out, World w) {
    }
}
