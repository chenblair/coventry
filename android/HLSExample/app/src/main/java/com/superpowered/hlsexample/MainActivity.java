package com.superpowered.hlsexample;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Locale;

import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

//TRy max speed on rotation sensor
//error location afterwards

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor rotVecSensor;
    private float[] rotationVector = new float[3];
    private float[] qVector = new float[4];


    // some HLS stream url-title pairs
    private String[] urls = new String[] {
            "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8", "Apple Advanced Example Stream",
            "http://qthttp.apple.com.edgesuite.net/1010qwoeiuryfg/sl.m3u8", "Back to the Mac",
            "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8", "JW Player Test",
            "http://playertest.longtailvideo.com/adaptive/oceans_aes/oceans_aes.m3u8", "JW AES Encrypted"
    };

    //private float bufferStartPercent = 0;
    private float bufferEndPercent = 0;
    private long durationSeconds = 0;
    private long lastDurationSeconds = 0;
    private long positionSeconds = 0;
    private long lastPositionSeconds = -1;
    private float positionPercent = 0;
    private int lastSeekProgress = -1;
    private int lastSecondaryProgress = -1;
    private boolean lastPlaying = false;
    private boolean playing = false;
    private boolean doubleSpeed = false;
    private Handler mHandler;
    private TextView currentTime;
    private TextView duration;
    private SeekBar seekBar;
    private SeekBar azimuthBar;
    private Button playPause;
    private RadioButton lastDownloadOption;
    private RadioGroup downloadOptions;
    private ArrayList<String>urlData = new ArrayList<>();
    private int selectedRow = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotVecSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        String samplerateString = null, buffersizeString = null;
        if (Build.VERSION.SDK_INT >= 17) {
            AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        }
        if (samplerateString == null) samplerateString = "44100";
        if (buffersizeString == null) buffersizeString = "512";

        System.loadLibrary("SuperpoweredExample");
        SetTempFolder(getCacheDir().getAbsolutePath());
        StartAudio(Integer.parseInt(samplerateString), Integer.parseInt(buffersizeString));

        // Set up the user interface
        currentTime = (TextView)findViewById(R.id.currentTime);
        if (currentTime != null) currentTime.setText("");
        duration = (TextView)findViewById(R.id.duration);
        seekBar = (SeekBar)findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Seek((float) (seekBar.getProgress()) / 100.0f);
            }
        });
        seekBar.setVisibility(View.INVISIBLE);

        azimuthBar = (SeekBar)findViewById(R.id.azimuthBar);
        azimuthBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SetAzimuth((float) 360.0* ((seekBar.getProgress()) / 100.0f));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        playPause = (Button)findViewById(R.id.playPause);
        lastDownloadOption = (RadioButton)findViewById(R.id.downloadRemaining);
        downloadOptions = (RadioGroup)findViewById(R.id.downloadOptions);
        ListView urlList = (ListView)findViewById(R.id.urlList);
        for (int n = 1; n < urls.length; n += 2) urlData.add(urls[n]);
        ArrayAdapter adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, urlData);
        if (urlList != null) {
            urlList.setAdapter(adapter);
            urlList.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long arg3) {
                    view.setSelected(true);
                    if (position != selectedRow) {
                        selectedRow = position;
                        Open(urls[position * 2]);
                    }
                }
            });
        }

        // Update the UI every 50 ms
        Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                UpdateStatus();
                if (durationSeconds >= 4294967295L) durationSeconds = -1;
                if (lastDurationSeconds != durationSeconds) {
                    lastDurationSeconds = durationSeconds;
                    if (durationSeconds > 0) {
                        duration.setText(String.format(Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60));
                        seekBar.setVisibility(View.VISIBLE);
                    } else if (durationSeconds == 0) {
                        final String loading = "Loading...";
                        duration.setText(loading);
                        currentTime.setText("");
                        seekBar.setVisibility(View.INVISIBLE);
                    } else {
                        final String live = "LIVE";
                        duration.setText(live);
                        seekBar.setVisibility(View.INVISIBLE);
                    }
                }
                if ((durationSeconds > 0) && (lastPositionSeconds != positionSeconds)) {
                    lastPositionSeconds = positionSeconds;
                    currentTime.setText(String.format(Locale.US, "%02d:%02d", positionSeconds / 60, positionSeconds % 60));
                }
                int secondaryProgress = (int)(bufferEndPercent * 100.0f), seekProgress = (int)(positionPercent * 100.0f);
                if ((lastSecondaryProgress != secondaryProgress) || (lastSeekProgress != seekProgress)) {
                    lastSecondaryProgress = secondaryProgress;
                    lastSeekProgress = seekProgress;
                    seekBar.setProgress(seekProgress);
                    seekBar.setSecondaryProgress(secondaryProgress);
                }
                if (lastPlaying != playing) {
                    lastPlaying = playing;
                    playPause.setText(lastPlaying ? "PAUSE" : "PLAY");
                }
                mHandler.postDelayed(this, 50);
            }
        };
        mHandler = new Handler();
        mHandler.postDelayed(mRunnable, 50);
    }

    public void onPlayPause(View view) {
        PlayPause();
    }

    public void onSpeed(View view) {
        doubleSpeed = !doubleSpeed;
        Button btn = (Button)view;
        btn.setText(doubleSpeed ? "2x SPEED" : "1x SPEED");
        SetSpeed(doubleSpeed);
    }

    public void onDownloadOption(View view) {
        RadioButton button = (RadioButton)view;
        if (button == lastDownloadOption) return;
        lastDownloadOption.setChecked(false);
        lastDownloadOption = button;
        button.setChecked(true);
        SetDownloadStrategy(downloadOptions.indexOfChild(view));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            rotationVector = event.values;
            float[] rotationV = new float[16];
            SensorManager.getRotationMatrixFromVector(rotationV, rotationVector);

            float[] orientationValuesV = new float[3];
            SensorManager.getOrientation(rotationV, orientationValuesV);
            double horizAngle = orientationValuesV[0]*(180/Math.PI);
            horizAngle = (horizAngle + 180+90)%360;
            azimuthBar.setProgress((int)(azimuthBar.getMax()*(horizAngle/360)));
            Log.v("Azimuth",Double.toString(horizAngle));
            SetAzimuth((float)(360.0-horizAngle));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor a, int s) {

    }

    @Override
    public void onResume() {
        super.onResume();
        onForeground();
        mSensorManager.registerListener(this, rotVecSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        onBackground();
    }

    protected void onDestroy() {
        super.onDestroy();
        Cleanup();
    }

    private native void SetTempFolder(String path);
    private native void StartAudio(int samplerate, int buffersize);
    private native void onForeground();
    private native void onBackground();
    private native void Open(String url);
    private native void Seek(float percent);
    private native void SetAzimuth(float position);
    private native void SetDownloadStrategy(int optionIndex);
    private native void PlayPause();
    private native void SetSpeed(boolean fast);
    private native void UpdateStatus();
    private native void Cleanup();
}
