package com.blairc.coventry;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by cherry on 2/9/18.
 */

public abstract class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

    protected void startLocationListener()  {
        final LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {

            }

            public void onProviderDisabled(String provider) {

            }

            public void onProviderEnabled(String provider) {

            }

            public void onStatusChanged (String provider, int status, Bundle extras) {

            }
        };
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, locationListener);
        } catch (SecurityException se) {
        }
    }
}
