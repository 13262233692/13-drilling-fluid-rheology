package com.drilling.buffer;

import com.drilling.modbus.ViscometerReader.ViscometerRawData;
import com.drilling.modbus.DensitometerReader.DensitometerRawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DataBuffer {

    private static final Logger log = LoggerFactory.getLogger(DataBuffer.class);

    private final SlidingWindowFilter theta600Window;
    private final SlidingWindowFilter theta300Window;
    private final SlidingWindowFilter theta200Window;
    private final SlidingWindowFilter theta100Window;
    private final SlidingWindowFilter theta6Window;
    private final SlidingWindowFilter theta3Window;
    private final SlidingWindowFilter gel10sWindow;
    private final SlidingWindowFilter gel10minWindow;
    private final SlidingWindowFilter densityWindow;
    private final SlidingWindowFilter temperatureWindow;

    private volatile ViscometerRawData latestViscometer;
    private volatile DensitometerRawData latestDensitometer;

    private final int windowSize;
    private final AtomicInteger pushCounter = new AtomicInteger(0);
    private final AtomicInteger filteredCalls = new AtomicInteger(0);

    public DataBuffer(@Value("${filter.sliding-window-size:10}") int windowSize) {
        this.windowSize = windowSize;
        this.theta600Window = SlidingWindowFilter.create(windowSize);
        this.theta300Window = SlidingWindowFilter.create(windowSize);
        this.theta200Window = SlidingWindowFilter.create(windowSize);
        this.theta100Window = SlidingWindowFilter.create(windowSize);
        this.theta6Window = SlidingWindowFilter.create(windowSize);
        this.theta3Window = SlidingWindowFilter.create(windowSize);
        this.gel10sWindow = SlidingWindowFilter.create(windowSize);
        this.gel10minWindow = SlidingWindowFilter.create(windowSize);
        this.densityWindow = SlidingWindowFilter.create(windowSize);
        this.temperatureWindow = SlidingWindowFilter.create(windowSize);
        log.info("DataBuffer initialized: slidingWindowSize={}, filterInstances=10", windowSize);
    }

    public void pushViscometerData(ViscometerRawData data) {
        if (data == null) {
            return;
        }
        latestViscometer = data;
        theta600Window.add(data.getTheta600());
        theta300Window.add(data.getTheta300());
        theta200Window.add(data.getTheta200());
        theta100Window.add(data.getTheta100());
        theta6Window.add(data.getTheta6());
        theta3Window.add(data.getTheta3());
        gel10sWindow.add(data.getGel10s());
        gel10minWindow.add(data.getGel10min());
        pushCounter.incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("Pushed viscometer (total={}): θ600={}, θ300={}",
                    pushCounter.get(), data.getTheta600(), data.getTheta300());
        }
    }

    public void pushDensitometerData(DensitometerRawData data) {
        if (data == null) {
            return;
        }
        latestDensitometer = data;
        densityWindow.add(data.getDensity());
        temperatureWindow.add(data.getTemperature());
        if (log.isDebugEnabled()) {
            log.debug("Pushed densitometer: density={}, temp={}",
                    data.getDensity(), data.getTemperature());
        }
    }

    public RheologyData getFilteredData() {
        ViscometerRawData vis = latestViscometer;
        DensitometerRawData den = latestDensitometer;
        if (vis == null && den == null) {
            return null;
        }

        double fTheta600 = theta600Window.getAverage();
        double fTheta300 = theta300Window.getAverage();
        double fTheta200 = theta200Window.getAverage();
        double fTheta100 = theta100Window.getAverage();
        double fTheta6 = theta6Window.getAverage();
        double fTheta3 = theta3Window.getAverage();
        double fGel10s = gel10sWindow.getAverage();
        double fGel10min = gel10minWindow.getAverage();
        double fDensity = densityWindow.getAverage();
        double fTemperature = temperatureWindow.getAverage();

        if (fTheta600 <= 0 || fTheta300 <= 0 || fTheta600 == fTheta300) {
            if (vis != null) {
                fTheta600 = vis.getTheta600();
                fTheta300 = vis.getTheta300();
                fTheta200 = vis.getTheta200();
                fTheta100 = vis.getTheta100();
                fTheta6 = vis.getTheta6();
                fTheta3 = vis.getTheta3();
                fGel10s = vis.getGel10s();
                fGel10min = vis.getGel10min();
            }
            if (den != null) {
                fDensity = den.getDensity();
                fTemperature = den.getTemperature();
            }
        }

        if (fTheta600 <= 0 || fTheta300 <= 0) {
            return null;
        }

        double pv = Math.max(0.0, fTheta600 - fTheta300);
        double yp = Math.max(0.0, fTheta300 - pv);
        double av = fTheta600 / 2.0;
        double ratio = fTheta600 / fTheta300;
        double n = ratio > 0 ? 3.322 * Math.log10(ratio) : 0.0;
        if (n <= 0.0 || n > 1.5) n = 0.5;
        double k = (511.0 * fTheta300) / Math.max(0.1, Math.pow(511.0, n));

        filteredCalls.incrementAndGet();
        return new RheologyData(
                System.currentTimeMillis(),
                fTheta600, fTheta300, fTheta200, fTheta100, fTheta6, fTheta3,
                fGel10s, fGel10min,
                pv, yp, av,
                fDensity, fTemperature,
                n, k
        );
    }

    public RheologyData getLatestRawData() {
        ViscometerRawData vis = latestViscometer;
        DensitometerRawData den = latestDensitometer;
        if (vis == null || den == null) {
            return null;
        }
        return RheologyData.calculateRheologyParams(vis, den);
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getPushCount() {
        return pushCounter.get();
    }

    public int getFilteredCallCount() {
        return filteredCalls.get();
    }
}
