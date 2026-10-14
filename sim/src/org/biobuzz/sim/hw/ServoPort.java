package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.PwmControl;

import org.biobuzz.sim.phys.ServoModel;

import java.util.LinkedHashMap;
import java.util.Map;

// One servo port: the last position the SDK sent, whether the PWM signal is on, and the pulse range.
public final class ServoPort {
    public final Hub hub;
    public final int index;
    public ServoModel servo;
    public boolean pwmEnabled;
    public double position = Double.NaN;
    public PwmControl.PwmRange range = PwmControl.PwmRange.defaultRange;

    ServoPort(Hub hub, int index) {
        this.hub = hub;
        this.index = index;
    }

    public double pulseUs() {
        return range.usPulseLower + position * (range.usPulseUpper - range.usPulseLower);
    }

    void firmware() {
        if (servo == null) return;
        servo.pulseUs = pwmEnabled && !Double.isNaN(position) ? pulseUs() : Double.NaN;
    }

    Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", index);
        m.put("part", servo == null ? null : servo.id);
        m.put("pwm", pwmEnabled);
        m.put("position", Double.isNaN(position) ? null : position);
        return m;
    }
}
