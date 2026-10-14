package org.biobuzz.sim.phys;

import java.util.Random;

// One POLLEN or NECTAR ball (Gopher ResisDent polyethylene, manual 9.8). Real balls vary a little in size and
// mass, so every element gets its own. s: inches along the robot's feed path, 0 = intake roller.
public final class Element {
    public enum Type { POLLEN, NECTAR }

    private static int nextId = 1;
    public final int id;
    public final Type type;
    public final double diameterIn, massKg, grip;
    public int lane = -1;          // -1 = still at the mouth or in the shared channel; 0 = front lane, 1 = back lane
    public double s;
    public int bounces;            // times a wrong-way flywheel pushed it back

    public Element(Type type, double nominalMassKg, Random rng) {
        synchronized (Element.class) {
            id = nextId++;
        }
        this.type = type;
        double nominal = type == Type.NECTAR ? 3.6 : 2.8;
        diameterIn = nominal + (rng.nextDouble() * 2 - 1) * 0.06;
        massKg = nominalMassKg * (1 + (rng.nextDouble() * 2 - 1) * 0.04);
        grip = 1 + (rng.nextDouble() * 2 - 1) * 0.02;
    }

    public double radiusIn() {
        return diameterIn / 2;
    }

    // passive sort by size: NECTAR rides up the ramp to the front launcher, POLLEN goes under it to the back
    public int sortLane() {
        return type == Type.NECTAR ? 0 : 1;
    }

    public static Type parse(String s, Type def) {
        if (s == null) return def;
        return s.trim().equalsIgnoreCase("POLLEN") ? Type.POLLEN : s.trim().equalsIgnoreCase("NECTAR") ? Type.NECTAR : def;
    }
}
