package com.superpowered.hlsexample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;

//TRy max speed on rotation sensor
//error location afterwards

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor rotVecSensor;
    private float[] rotationVector = new float[3];
    private float[] qVector = new float[4];
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    PowerManager pm;
    private PowerManager.WakeLock wakeLock;

    private Location destinationLocation;
    private Location currentLocation;
    private Location startLocation;




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

        // some HLS stream url-title pairs
        final String[] urls = new String[] {
                "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8", "Apple Advanced Example Stream",
                "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Man Down.mp3", "Man Down",
                "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8", "JW Player Test",
                "http://playertest.longtailvideo.com/adaptive/oceans_aes/oceans_aes.m3u8", "JW AES Encrypted",
                "raw://fb.mp3", "FB",
                Uri.parse("android.resource://"+this.getPackageName()+"/raw/gm").getPath(), "GM2",
                Uri.parse("android.resource://"+this.getPackageName()+"/raw/gm.mp3").getPath(), "GM2"


        };

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
                        OpenFile(urls[position * 2]);
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
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startPicker();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakelock");;
        wakeLock.acquire();
    }

    protected void startPicker() {
        int PLACE_PICKER_REQUEST = 1;
        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();

        try {
            startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST);
        } catch (GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        wakeLock.release();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Place place = PlacePicker.getPlace(this,data);
            //String toastMsg = String.format("Place: %s", place.getName());
            //Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
            destinationLocation = new Location("");
            destinationLocation.setLatitude(place.getLatLng().latitude);
            destinationLocation.setLongitude(place.getLatLng().longitude);
        }
    }

    private void startLocationUpdates() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                    if (startLocation == null) {
                        startLocation = location;
                    }
                    currentLocation = location;
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    Log.v("GPS:", "IN ON LOCATION CHANGE, lat=" + latitude + ", lon=" + longitude);
                    Toast.makeText(getApplicationContext(), Double.toString(latitude), Toast.LENGTH_LONG).show();
            }
        };


        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        69);
        }
        else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback,
                    null /* Looper */);
        }

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


            float thetaDest = 0;
            if (currentLocation != null && destinationLocation !=null) {
                thetaDest = currentLocation.bearingTo(destinationLocation);

                if (startLocation != null) {
                    float scaleFactor = 1-(currentLocation.distanceTo(destinationLocation) / startLocation.distanceTo(destinationLocation));
                    float vol = (float)Math.max(0.2,(-Math.log(-(scaleFactor-1.0)/scaleFactor)+3.0)/6.0);
                    SetSpatialVolume((float)vol);
                    Log.v("distToDest",Float.toString(currentLocation.distanceTo(destinationLocation)));
                    Log.v("distStartDest",Float.toString(startLocation.distanceTo(destinationLocation)));
                }

            }

            SetAzimuth((float)((360-horizAngle+thetaDest))%360);

        }
    }

    @Override
    public void onAccuracyChanged(Sensor a, int accuracy) {

    }

    @Override
    public void onResume() {
        super.onResume();
        onForeground();
        mSensorManager.registerListener(this, rotVecSensor, SensorManager.SENSOR_DELAY_GAME);
        startLocationUpdates();
    }

    @Override
    public void onPause() {
        super.onPause();
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
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
    private native void OpenHLS(String url);
    private native void OpenFile(String url);
    private native void Seek(float percent);
    private native void SetAzimuth(float position);
    private native void SetSpatialVolume(float volume);
    private native void SetDownloadStrategy(int optionIndex);
    private native void PlayPause();
    private native void SetSpeed(boolean fast);
    private native void UpdateStatus();
    private native void Cleanup();
}
