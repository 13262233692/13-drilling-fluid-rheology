package com.drilling.buffer;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowFilter {

    private final double[] buffer;
    private final int windowSize;
    private int count;
    private int head;
    private final ReentrantLock lock = new ReentrantLock();

    public SlidingWindowFilter(@Value("${filter.sliding-window-size:10}") int windowSize) {
        this.windowSize = windowSize;
        this.buffer = new double[windowSize];
        this.count = 0;
        this.head = 0;
    }

    private SlidingWindowFilter(int windowSize, boolean standalone) {
        this.windowSize = windowSize;
        this.buffer = new double[windowSize];
        this.count = 0;
        this.head = 0;
    }

    public static SlidingWindowFilter create(int size) {
        return new SlidingWindowFilter(size, true);
    }

    public void add(double value) {
        lock.lock();
        try {
            buffer[head] = value;
            head = (head + 1) % windowSize;
            if (count < windowSize) {
                count++;
            }
        } finally {
            lock.unlock();
        }
    }

    public double getAverage() {
        lock.lock();
        try {
            if (count == 0) {
                return 0.0;
            }
            double sum = 0.0;
            for (int i = 0; i < count; i++) {
                sum += buffer[i];
            }
            return sum / count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        lock.lock();
        try {
            return count == windowSize;
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            count = 0;
            head = 0;
        } finally {
            lock.unlock();
        }
    }
}
