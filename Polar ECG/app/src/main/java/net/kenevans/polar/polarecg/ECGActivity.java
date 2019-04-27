package net.kenevans.polar.polarecg;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;
import com.polar.polarecg.R;

import org.reactivestreams.Publisher;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarSensorSetting;

public class ECGActivity extends AppCompatActivity implements PlotterListener {
    private static final int POINTS_TO_PLOT = 1000;
    private XYPlot plot;
    private Plotter plotter;

    TextView textViewHR, textViewFW;
    private String TAG = "Polar_ECGActivity";
    public PolarBleApi api;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID;

    private long time0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);
        DEVICE_ID = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info);
        textViewFW = findViewById(R.id.fw);

        plot = findViewById(R.id.plot);

        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void polarDeviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(classContext, R.string.connected,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void polarDeviceConnecting(PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void polarDeviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);

            }

            @Override
            public void ecgFeatureReady(String s) {
                Log.d(TAG, "ECG Feature ready " + s);
                streamECG();
            }

            @Override
            public void accelerometerFeatureReady(String s) {
                Log.d(TAG, "ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(String s) {
                Log.d(TAG, "PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(String s) {
                Log.d(TAG, "PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void fwInformationReceived(String s, String s1) {
                String msg = "Firmware: " + s1.trim();
                Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                textViewFW.append(msg + "\n");
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);
                textViewHR.setText(String.valueOf(polarHrData.hr));
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        api.connectToPolarDevice(DEVICE_ID);

        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float dpHeight = displayMetrics.heightPixels / displayMetrics.density;
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        Log.d(TAG, "dpWidth=" + dpWidth + " dpHeight=" + dpHeight);
        Log.d(TAG, "widthPixels=" + displayMetrics.widthPixels +
                " heightPixels=" + displayMetrics.heightPixels);
        Log.d(TAG, "density=" + displayMetrics.density);
        Log.d(TAG, "10dp=" + 10 / displayMetrics.density + " pixels");

        plotter = new Plotter(this, POINTS_TO_PLOT, "ECG", Color.RED, false);
        plotter.setListener(this);

        plot.addSeries(plotter.getSeries(), plotter.getFormatter());
        plot.setRangeBoundaries(-3.3 * POINTS_TO_PLOT / 500,
                3.3 * POINTS_TO_PLOT / 500, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, .5 * POINTS_TO_PLOT / 500);
        plot.setDomainBoundaries(0, POINTS_TO_PLOT, BoundaryMode.FIXED);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL,
                26.05 * POINTS_TO_PLOT / 500);

        Log.d(TAG, "plotWidth=" + plot.getWidth() +
                " plotHeight=" + plot.getHeight());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }

    public void streamECG() {
        if (ecgDisposable == null) {
            ecgDisposable =
                    api.requestEcgSettings(DEVICE_ID).toFlowable().flatMap(new Function<PolarSensorSetting, Publisher<PolarEcgData>>() {
                        @Override
                        public Publisher<PolarEcgData> apply(PolarSensorSetting sensorSetting) {
//                            Log.d(TAG, "ecgDisposable requestEcgSettings " +
//                                    "apply");
//                            Log.d(TAG,
//                                    "sampleRate=" + sensorSetting
//                                    .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.SAMPLE_RATE) +
//                                            " resolution=" + sensorSetting
//                                            .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.RESOLUTION) +
//                                            " range=" + sensorSetting
//                                            .maxSettings().settings.
//                                            get(PolarSensorSetting
//                                            .SettingType.RANGE));
                            return api.startEcgStreaming(DEVICE_ID,
                                    sensorSetting.maxSettings());
                        }
                    }).observeOn(AndroidSchedulers.mainThread()).subscribe(
                            new Consumer<PolarEcgData>() {
                                @Override
                                public void accept(PolarEcgData polarEcgData) {
//                                    double deltaT =
//                                            .000000001 * (polarEcgData
//                                            .timeStamp - time0);
//                                    time0 = polarEcgData.timeStamp;
//                                    int nSamples = polarEcgData.samples
//                                    .size();
//                                    double samplesPerSec = nSamples / deltaT;
//                                    Log.d(TAG,
//                                            "ecg update:" +
//                                                    " deltaT=" + String
//                                                    .format("%.3f", deltaT) +
//                                                    " nSamples=" + nSamples +
//                                                    " samplesPerSec=" +
//                                                    String.format("%.3f",
//                                                    samplesPerSec));
                                    plotter.addValues(plot, polarEcgData);
                                }
                            },
                            new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) {
                                    Log.e(TAG,
                                            "" + throwable.getLocalizedMessage());
                                    ecgDisposable = null;
                                }
                            },
                            new Action() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "complete");
                                }
                            }
                    );
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable.dispose();
            ecgDisposable = null;
        }
    }

    @Override
    public void update() {
//        Log.d(TAG, "plotWidth=" + plot.getWidth() +
//                " plotHeight=" + plot.getHeight());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                plot.redraw();
            }
        });
    }
}
