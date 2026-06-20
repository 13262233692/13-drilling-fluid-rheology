package com.drilling.timeseries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ARIMAPredictor {
    private static final Logger log = LoggerFactory.getLogger(ARIMAPredictor.class);

    private final int p;
    private final int d;
    private final int q;

    public ARIMAPredictor(@Value("${arima.p:3}") int p,
                          @Value("${arima.d:1}") int d,
                          @Value("${arima.q:1}") int q) {
        this.p = p;
        this.d = d;
        this.q = q;
    }

    public double[] forecast(double[] data, int steps) {
        return forecastStatic(data, p, d, q, steps);
    }

    public static double[] forecastStatic(double[] data, int p, int d, int q, int steps) {
        if (data == null || data.length < p + d + 1) {
            throw new IllegalArgumentException("Insufficient data for ARIMA(" + p + "," + d + "," + q + ")");
        }
        if (steps <= 0) {
            return new double[0];
        }

        double[] lastValues = new double[d];
        for (int i = 0; i < d; i++) {
            lastValues[i] = data[data.length - 1 - i];
        }

        double[] diffed = difference(data, d);

        double[] arCoeffs = fitAR(diffed, p);

        double[] arPredictions = new double[diffed.length];
        for (int i = p; i < diffed.length; i++) {
            double pred = 0;
            for (int j = 0; j < p; j++) {
                pred += arCoeffs[j] * diffed[i - 1 - j];
            }
            arPredictions[i] = pred;
        }

        double[] residuals = new double[diffed.length];
        for (int i = p; i < diffed.length; i++) {
            residuals[i] = diffed[i] - arPredictions[i];
        }

        double[] maCoeffs = fitMA(residuals, q);

        double[] diffForecast = new double[steps];
        double[] extended = new double[diffed.length + steps];
        System.arraycopy(diffed, 0, extended, 0, diffed.length);

        double[] extendedResiduals = new double[residuals.length + steps];
        System.arraycopy(residuals, 0, extendedResiduals, 0, residuals.length);

        for (int s = 0; s < steps; s++) {
            int idx = diffed.length + s;
            double arPart = 0;
            for (int j = 0; j < p; j++) {
                arPart += arCoeffs[j] * extended[idx - 1 - j];
            }

            double maPart = 0;
            for (int j = 0; j < q; j++) {
                int residIdx = idx - 1 - j;
                if (residIdx >= 0 && residIdx < residuals.length) {
                    maPart += maCoeffs[j] * extendedResiduals[residIdx];
                }
            }

            diffForecast[s] = arPart + maPart;
            extended[idx] = diffForecast[s];
            extendedResiduals[idx] = 0;
        }

        double[] forecast = integrate(diffForecast, d, lastValues);

        log.debug("ARIMA({},{},{}) forecast completed for {} steps", p, d, q, steps);
        return forecast;
    }

    private static double[] difference(double[] data, int order) {
        double[] current = data;
        for (int i = 0; i < order; i++) {
            double[] diff = new double[current.length - 1];
            for (int j = 0; j < diff.length; j++) {
                diff[j] = current[j + 1] - current[j];
            }
            current = diff;
        }
        return current;
    }

    private static double[] integrate(double[] diff, int order, double[] lastValues) {
        double[] current = diff;
        for (int i = 0; i < order; i++) {
            double lastVal = lastValues[i];
            double[] integrated = new double[current.length];
            double prev = lastVal;
            for (int j = 0; j < current.length; j++) {
                integrated[j] = prev + current[j];
                prev = integrated[j];
            }
            current = integrated;
        }
        return current;
    }

    private static double[] fitAR(double[] data, int p) {
        int n = data.length;
        double[] r = new double[p + 1];

        for (int k = 0; k <= p; k++) {
            double sum = 0;
            for (int i = 0; i < n - k; i++) {
                sum += data[i] * data[i + k];
            }
            r[k] = sum / n;
        }

        double[] coeffs = levinsonDurbin(r, p);

        double variance = r[0];
        for (int i = 0; i < p; i++) {
            variance -= coeffs[i] * r[i + 1];
        }

        log.debug("AR({}) fitted, variance: {}", p, variance);
        return coeffs;
    }

    private static double[] levinsonDurbin(double[] r, int p) {
        double[] coeffs = new double[p];
        double[] prevCoeffs = new double[p];
        double k, e = r[0];

        for (int i = 1; i <= p; i++) {
            double sum = 0;
            for (int j = 1; j < i; j++) {
                sum += prevCoeffs[j - 1] * r[i - j];
            }
            k = (r[i] - sum) / e;

            coeffs[i - 1] = k;
            for (int j = 1; j < i; j++) {
                coeffs[j - 1] = prevCoeffs[j - 1] - k * prevCoeffs[i - j - 1];
            }

            e = e * (1 - k * k);

            System.arraycopy(coeffs, 0, prevCoeffs, 0, i);
        }

        return coeffs;
    }

    private static double[] fitMA(double[] residuals, int q) {
        int n = residuals.length;
        double[] maCoeffs = new double[q];

        if (q == 0 || n <= q) {
            return maCoeffs;
        }

        double[] r = new double[q + 1];
        for (int k = 0; k <= q; k++) {
            double sum = 0;
            int count = 0;
            for (int i = q; i < n - k; i++) {
                sum += residuals[i] * residuals[i + k];
                count++;
            }
            r[k] = count > 0 ? sum / count : 0;
        }

        double variance = r[0];
        double[] theta = new double[q];
        double[] v = new double[q + 1];
        v[0] = variance;

        for (int i = 1; i <= q; i++) {
            double sum = 0;
            for (int j = 1; j < i; j++) {
                sum += theta[j - 1] * r[i - j];
            }

            if (v[i - 1] == 0) {
                theta[i - 1] = 0;
            } else {
                theta[i - 1] = (r[i] - sum) / v[i - 1];
            }

            v[i] = v[i - 1] * (1 - theta[i - 1] * theta[i - 1]);

            for (int j = 1; j < i; j++) {
                theta[j - 1] = theta[j - 1] - theta[i - 1] * theta[i - j - 1];
            }
        }

        for (int i = 0; i < q; i++) {
            maCoeffs[i] = theta[i];
        }

        log.debug("MA({}) fitted", q);
        return maCoeffs;
    }

    public int getP() {
        return p;
    }

    public int getD() {
        return d;
    }

    public int getQ() {
        return q;
    }
}
