package com.u1x.damagestats.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class TraceRingBuffer<T> {
    private final int capacity;
    private final Deque<T> entries = new ArrayDeque<>();

    TraceRingBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    void add(T entry) {
        entries.addFirst(entry);
        while (entries.size() > capacity) entries.removeLast();
    }

    T latest() {
        return entries.peekFirst();
    }

    List<T> newest(int count) {
        List<T> result = new ArrayList<>(Math.min(count, entries.size()));
        int remaining = count;
        for (T entry : entries) {
            if (remaining-- <= 0) break;
            result.add(entry);
        }
        return List.copyOf(result);
    }
}
