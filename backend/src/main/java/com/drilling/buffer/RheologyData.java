package com.drilling.buffer;

import com.drilling.modbus.ViscometerReader.ViscometerRawData;
import com.drilling.modbus.DensitometerReader.DensitometerRawData;

public class RheologyData {

    private final long timestamp;
    private final double theta600;
    private final double theta300;
    private final double theta200;
    private final double theta100;
    private final double theta6;
    private final double theta3;
    private final double gel10s;
    private final double gel10min;
    private final double plasticViscosity;
    private final double yieldPoint;
    private final double apparentViscosity;
    private final double density;
    private final double temperature;
    private final double flowBehaviorIndex;
    private final double consistencyIndex;

    public RheologyData(long timestamp, double theta600, double theta300, double theta200,
                        double theta100, double theta6, double theta3, double gel10s,
                        double gel10min, double plasticViscosity, double yieldPoint,
                        double apparentViscosity, double density, double temperature,
                        double flowBehaviorIndex, double consistencyIndex) {
        this.timestamp = timestamp;
        this.theta600 = theta600;
        this.theta300 = theta300;
        this.theta200 = theta200;
        this.theta100 = theta100;
        this.theta6 = theta6;
        this.theta3 = theta3;
        this.gel10s = gel10s;
        this.gel10min = gel10min;
        this.plasticViscosity = plasticViscosity;
        this.yieldPoint = yieldPoint;
        this.apparentViscosity = apparentViscosity;
        this.density = density;
        this.temperature = temperature;
        this.flowBehaviorIndex = flowBehaviorIndex;
        this.consistencyIndex = consistencyIndex;
    }

    public long getTimestamp() { return timestamp; }
    public double getTheta600() { return theta600; }
    public double getTheta300() { return theta300; }
    public double getTheta200() { return theta200; }
    public double getTheta100() { return theta100; }
    public double getTheta6() { return theta6; }
    public double getTheta3() { return theta3; }
    public double getGel10s() { return gel10s; }
    public double getGel10min() { return gel10min; }
    public double getPlasticViscosity() { return plasticViscosity; }
    public double getYieldPoint() { return yieldPoint; }
    public double getApparentViscosity() { return apparentViscosity; }
    public double getDensity() { return density; }
    public double getTemperature() { return temperature; }
    public double getFlowBehaviorIndex() { return flowBehaviorIndex; }
    public double getConsistencyIndex() { return consistencyIndex; }

    public static RheologyData calculateRheologyParams(ViscometerRawData viscometer, DensitometerRawData densitometer) {
        double pv = viscometer.getTheta600() - viscometer.getTheta300();
        double yp = viscometer.getTheta300() - pv;
        double av = viscometer.getTheta600() / 2.0;
        double n = 3.322 * Math.log10(viscometer.getTheta600() / viscometer.getTheta300());
        double k = (511.0 * viscometer.getTheta300()) / Math.pow(511.0, n);

        return new RheologyData(
                System.currentTimeMillis(),
                viscometer.getTheta600(),
                viscometer.getTheta300(),
                viscometer.getTheta200(),
                viscometer.getTheta100(),
                viscometer.getTheta6(),
                viscometer.getTheta3(),
                viscometer.getGel10s(),
                viscometer.getGel10min(),
                pv,
                yp,
                av,
                densitometer.getDensity(),
                densitometer.getTemperature(),
                n,
                k
        );
    }
}
