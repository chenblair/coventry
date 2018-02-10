/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.vr.sdk.samples.treasurehunt;

import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.Sensor;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.hardware.SensorEventListener;
import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.GvrActivity;

public class TreasureHuntActivity extends GvrActivity implements SensorEventListener {
  private SensorManager mSensorManager;
  private Sensor mAccelerometer;
  private Sensor mMagnetometer;
  private Sensor rotVecSensor;

  private final float[] mOrientationAngles = new float[3];
  private float[] mAccelerometerReading = new float[3];
  private float[] mMagnetometerReading = new float[3];
  private float[] rotationVector = new float[3];
  private float[] qVector = new float[4];

  protected float[] modelPosition = {(float)0, (float)0, (float)-20};
  protected Location curPosition;

  private static final String OBJECT_SOUND_FILE = "bell.wav";

  private GvrAudioEngine gvrAudioEngine;
  private volatile int sourceId = GvrAudioEngine.INVALID_ID;

  /**
   * Sets the view to our GvrView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    mAccelerometer = (Sensor)mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    mMagnetometer = (Sensor)mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    rotVecSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);

                sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
                gvrAudioEngine.setSoundVolume(sourceId, 1);
                gvrAudioEngine.setSoundObjectPosition(sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
                gvrAudioEngine.playSound(sourceId, true /* looped playback */);
              }
            })
            .start();
  }

  @Override
  public void onPause() {
    super.onPause();

    gvrAudioEngine.pause();
    mSensorManager.unregisterListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    gvrAudioEngine.resume();

//    mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
//    mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
    mSensorManager.registerListener(this, rotVecSensor, SensorManager.SENSOR_DELAY_GAME);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Do something here if sensor accuracy changes.
    // You must implement this callback in your code.
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    float[] rotationMatrix = new float[9];
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      System.arraycopy(event.values, 0, mAccelerometerReading,0, 3);
    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
      System.arraycopy(event.values, 0, mMagnetometerReading,0, 3);
    }

//    SensorManager.getRotationMatrix(rotationMatrix, null, mAccelerometerReading, mMagnetometerReading);
//    SensorManager.getOrientation(rotationMatrix, mOrientationAngles);

//    rotationVector[0] = (float)Math.sin(mOrientationAngles[0]);
//    rotationVector[1] = 0;
//    rotationVector[2] = (float)Math.cos(mOrientationAngles[0]);;

    event.values[0] = event.values[0];
    event.values[1] = event.values[2];
    event.values[2] = 0;

    SensorManager.getQuaternionFromVector(qVector, event.values);
    gvrAudioEngine.setHeadRotation(qVector[1], qVector[2], qVector[3], qVector[0]);
    gvrAudioEngine.update();

//    System.out.println((mOrientationAngles[0] * 180 / (Math.PI)) + 90);
  }
}
