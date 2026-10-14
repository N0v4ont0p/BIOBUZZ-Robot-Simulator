package org.firstinspires.ftc.teamcode.pedro.procedures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Stands in for Pedro's Inputs: the questions a tuner asks. The Screen fills them in.
public class Inputs {
    public enum Type { STRING, DOUBLE, INTEGER, BOOLEAN, ENUM }

    public static class Field<Value> {
        public final String name;
        public final Type type;
        public final Class<?> enumClass;
        private Value value, defaultValue;
        private boolean set;

        Field(String name, Type type, Class<?> enumClass) {
            this.name = name;
            this.type = type;
            this.enumClass = enumClass;
        }

        public Field<Value> withDefault(Value value) {
            defaultValue = value;
            return this;
        }

        public Field<Value> withoutDefault() {
            defaultValue = null;
            return this;
        }

        public Value defaultValue() {
            return defaultValue;
        }

        public Value get() {
            return set ? value : defaultValue;
        }

        public boolean isSet() {
            return set;
        }

        @SuppressWarnings("unchecked")
        public void set(Object value) {
            this.value = (Value) value;
            set = true;
        }
    }

    public final String name;
    public final String description;
    private final List<Field<?>> fields = new ArrayList<>();

    public Inputs(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public List<Field<?>> fields() {
        return Collections.unmodifiableList(fields);
    }

    private <V> Field<V> add(Field<V> f) {
        fields.add(f);
        return f;
    }

    public final Field<String> s(String name) {
        return add(new Field<>(name, Type.STRING, null));
    }

    public final Field<Double> d(String name) {
        return add(new Field<>(name, Type.DOUBLE, null));
    }

    public final Field<Integer> i(String name) {
        return add(new Field<>(name, Type.INTEGER, null));
    }

    public final Field<Boolean> b(String name) {
        return add(new Field<>(name, Type.BOOLEAN, null));
    }

    public final <Value extends Enum<Value>> Field<Value> e(String name, Class<Value> type) {
        return add(new Field<>(name, Type.ENUM, type));
    }
}
