package android.util;
public class LongSparseArray<E> {
    private final java.util.HashMap<Long, E> m = new java.util.HashMap<>();
    public E get(long k) { return m.get(k); }
    public E get(long k, E d) { return m.getOrDefault(k, d); }
    public void put(long k, E v) { m.put(k, v); }
    public void remove(long k) { m.remove(k); }
    public void delete(long k) { m.remove(k); }
    public int size() { return m.size(); }
}
