package com.drilling.buffer;

import com.drilling.modbus.ViscometerReader.ViscometerRawData;
import com.drilling.modbus.DensitometerReader.DensitometerRawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public DataBuffer(@Value("${filter.sliding-window-size:10}") int windowSize) {
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
    }

    public void pushViscometerData(ViscometerRawData data) {
        latestViscometer = data;
        theta600Window.add(data.getTheta600());
        theta300Window.add(data.getTheta300());
        theta200Window.add(data.getTheta200());
        theta100Window.add(data.getTheta100());
        theta6Window.add(data.getTheta6());
        theta3Window.add(data.getTheta3());
        gel10sWindow.add(data.getGel10s());
        gel10minWindow.add(data.getGel10min());
        log.debug("Pushed viscometer data: θ600={}, θ300={}", data.getTheta600(), data.getTheta300());
    }

    public void pushDensitometerData(DensitometerRawData data) {
        latestDensitometer = data;
        densityWindow.add(data.getDensity());
        temperatureWindow.add(data.getTemperature());
        log.debug("Pushed densitometer data: density={}, temp={}", data.getDensity(), data.getTemperature());
    }

    public RheologyData getFilteredData() {
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

        double pv = fTheta600 - fTheta300;
        double yp = fTheta300 - pv;
        double av = fTheta600 / 2.0;
        double n = 3.322 * Math.log10(fTheta600 / fTheta300);
        double k = (511.0 * fTheta300) / Math.pow(511.0, n);

        return new RheologyData(
                System.currentTimeMillis(),
                fTheta600,
                fTheta300,
                fTheta200,
                fTheta100,
                fTheta6,
                fTheta3,
                fGel10s,
                fGel10min,
                pv,
                yp,
                av,
                fDensity,
                fTemperature,
                n,
                k
        );
    }

    public RheologyData getLatestRawData() {
        if (latestViscometer == null || latestDensitometer == null) {
            return null;
        }
        return RheologyData.calculateRheologyParams(latestViscometer, latestDensitometer);
    }
}
