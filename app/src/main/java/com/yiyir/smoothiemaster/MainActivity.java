package com.yiyir.smoothiemaster;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements Observer {
    private final ObservableBlockingQueue queue = new ObservableBlockingQueue();
    private final String TAG = "Smoothie_Master";
    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int BUFFER_SIZE_FACTOR = 13;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Button button;
    private TextView smoothieStatus;
    private GraphView graph1;
    private GraphView graph2;
    private LineGraphSeries<DataPoint> series1 = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> series2 = new LineGraphSeries<>();
    private boolean isRunning = false;
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);

    private final double SMOOTHING = 3.5;
    private int counter = 0;
    private double std = 0.0;
    private double prev = 0.0;
    private double diff = 0.0;
    private boolean dropDOwn = false;
    private int valleyStartIndex = 0;
    private double valleyWidth = 0;
    private double valleyStartValue = 0.0;
    private double valleyDepth = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.start_button);
        smoothieStatus = (TextView) findViewById(R.id.smoothie_status);
        graph1 = (GraphView) findViewById(R.id.standard_deviation);
        graph1.getViewport().setYAxisBoundsManual(true);
        graph1.getViewport().setXAxisBoundsManual(true);
        graph1.getViewport().setMinX(4);
        graph1.getViewport().setMaxX(80);
        graph1.getViewport().setMinY(0);
        graph1.getViewport().setMaxY(0.12);
        graph1.addSeries(series1);
        graph2 = (GraphView) findViewById(R.id.denoised_standard_deviation);
        graph2.getViewport().setYAxisBoundsManual(true);
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setMinX(4);
        graph2.getViewport().setMaxX(80);
        graph2.getViewport().setMinY(0);
        graph2.getViewport().setMaxY(0.12);
        graph2.addSeries(series2);
        queue.addObserver(this);
    }

    public void pressButton(View view) {
        if (!isRunning) {
            startRecording();
            isRunning = true;
            button.setText("STOP");
        } else {
            stopRecording();
            isRunning = false;
            button.setText("START");
        }
    }

    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

        recorder.startRecording();

        recordingInProgress.set(true);

        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    private void stopRecording() {
        if (null == recorder) {
            return;
        }

        recordingInProgress.set(false);

        recorder.stop();

        recorder.release();

        recorder = null;

        recordingThread = null;
    }

    @Override
    public void update(Observable observable, Object o) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                float[] data = new float[BUFFER_SIZE];
                try {
                    data = queue.dequeueData();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Statistics newStat = new Statistics(data);
                final double rawStd = newStat.getStdDev();
                Log.d(TAG, "the rawStd: " + rawStd);
                std += (rawStd - std) / SMOOTHING;
                // calculate the difference(derivative) at the current point of time
                diff = std - prev;
                // peak detection
                if (diff < 0) {
                    if (!dropDOwn) {
                        if (valleyStartIndex != 0) {
                            valleyWidth = counter - valleyStartIndex;
                        }
                        valleyStartIndex = counter;
                        valleyStartValue = std;
                    }
                    dropDOwn = true;
                } else {
                    if (dropDOwn) {
                        valleyDepth = valleyStartValue - prev;

//                        Log.d(TAG, "height: " + valleyHeight + "    width: " + valleyWidth);
                        if (std < 0.05 && valleyWidth > 5) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    smoothieStatus.setText("Your smoothie is done!");
                                }
                            });
                        }
                        dropDOwn = false;
                    }
                }
                prev = std;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        series1.appendData(new DataPoint(counter, rawStd), true, 1000);
                        series2.appendData(new DataPoint(counter, std), true, 1000);
                    }
                });

            }
        });

    }

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {
            final float[] data = new float[BUFFER_SIZE];
            while (recordingInProgress.get()) {
                int result = recorder.read(data, 0, SAMPLING_RATE_IN_HZ / 10, AudioRecord.READ_BLOCKING);
                if (result < 0) {
                    throw new RuntimeException("Reading of audio buffer failed: " +
                            getBufferReadFailureReason(result));
                }
                try {
                    queue.enqueueData(data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                counter++;


            }
        }
    }


    private String getBufferReadFailureReason(int errorCode) {
        switch (errorCode) {
            case AudioRecord.ERROR_INVALID_OPERATION:
                return "ERROR_INVALID_OPERATION";
            case AudioRecord.ERROR_BAD_VALUE:
                return "ERROR_BAD_VALUE";
            case AudioRecord.ERROR_DEAD_OBJECT:
                return "ERROR_DEAD_OBJECT";
            case AudioRecord.ERROR:
                return "ERROR";
            default:
                return "Unknown (" + errorCode + ")";
        }
    }


    private class Statistics {

        float[] data;
        int size;

        public Statistics(float[] data) {
            this.data = data;
            size = data.length;
        }

        private double getMean() {
            double sum = 0.0;
            for (double a : data)
                sum += a;
            return sum / size;
        }

        private double getVariance() {
            double mean = getMean();
            double temp = 0;
            for (double a : data)
                temp += (a - mean) * (a - mean);
            return temp / (size - 1);
        }

        double getStdDev() {
            return Math.sqrt(getVariance());
        }
    }

}
