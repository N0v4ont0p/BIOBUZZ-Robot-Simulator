package org.biobuzz.sim.rc;

import com.google.gson.JsonObject;

import com.qualcomm.robotcore.hardware.Gamepad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Gamepad traffic in both directions: browser controller state in (through the SDK's own Gamepad byte format,
// exactly what the Driver Station sends over Wi-Fi), rumble and LED effects out.
final class Effects {
    private static final String[] BUTTONS = {"cross", "circle", "square", "triangle", "share", "options", "ps", "touchpad",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right", "left_bumper", "right_bumper", "left_stick_button", "right_stick_button"};

    static Gamepad fromBrowser(JsonObject j) {
        Gamepad g = new Gamepad();
        if (j == null) return wire(g);
        g.type = Gamepad.Type.SONY_PS4;
        g.left_stick_x = f(j, "left_stick_x");
        g.left_stick_y = f(j, "left_stick_y");
        g.right_stick_x = f(j, "right_stick_x");
        g.right_stick_y = f(j, "right_stick_y");
        g.left_trigger = f(j, "left_trigger");
        g.right_trigger = f(j, "right_trigger");
        g.cross = b(j, "cross");
        g.circle = b(j, "circle");
        g.square = b(j, "square");
        g.triangle = b(j, "triangle");
        g.share = b(j, "share");
        g.options = b(j, "options");
        g.ps = b(j, "ps");
        g.touchpad = b(j, "touchpad");
        g.dpad_up = b(j, "dpad_up");
        g.dpad_down = b(j, "dpad_down");
        g.dpad_left = b(j, "dpad_left");
        g.dpad_right = b(j, "dpad_right");
        g.left_bumper = b(j, "left_bumper");
        g.right_bumper = b(j, "right_bumper");
        g.left_stick_button = b(j, "left_stick_button");
        g.right_stick_button = b(j, "right_stick_button");
        g.a = g.cross;
        g.b = g.circle;
        g.x = g.square;
        g.y = g.triangle;
        g.back = g.share;
        g.start = g.options;
        g.guide = g.ps;
        return wire(g);
    }

    // through the wire format, like a real Driver Station packet
    private static Gamepad wire(Gamepad g) {
        try {
            Gamepad rc = new Gamepad();
            rc.fromByteArray(g.toByteArray());
            return rc;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String[] buttons() {
        return BUTTONS;
    }

    private static float f(JsonObject j, String k) {
        return j.has(k) ? j.get(k).getAsFloat() : 0f;
    }

    private static boolean b(JsonObject j, String k) {
        return j.has(k) && j.get(k).getAsBoolean();
    }

    static Map<String, Object> rumble(int user, Gamepad.RumbleEffect r) {
        List<Object> steps = new ArrayList<>();
        for (Gamepad.RumbleEffect.Step s : r.steps) steps.add(new double[]{s.large / 255.0, s.small / 255.0, s.duration});
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user);
        m.put("steps", steps);
        return m;
    }

    static Map<String, Object> led(int user, Gamepad.LedEffect l) {
        List<Object> steps = new ArrayList<>();
        for (Gamepad.LedEffect.Step s : l.steps) steps.add(new int[]{s.r, s.g, s.b, s.duration});
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user);
        m.put("steps", steps);
        m.put("repeating", l.repeating);
        return m;
    }
}
