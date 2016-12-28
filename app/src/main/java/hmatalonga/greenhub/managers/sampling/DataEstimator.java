/*
 * Copyright (C) 2016 Hugo Matalonga & João Paulo Fernandes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hmatalonga.greenhub.managers.sampling;


import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import hmatalonga.greenhub.Config;
import hmatalonga.greenhub.events.BatteryLevelEvent;
import hmatalonga.greenhub.models.LocationInfo;
import hmatalonga.greenhub.models.data.LocationProvider;

import static hmatalonga.greenhub.util.LogUtils.LOGI;
import static hmatalonga.greenhub.util.LogUtils.makeLogTag;

/**
 * Provides current Device data readings.
 *
 * Created by hugo on 09-04-2016.
 */
public class DataEstimator extends WakefulBroadcastReceiver implements LocationListener {

    private static final String TAG = makeLogTag(DataEstimator.class);

    private Location lastKnownLocation = null;
    private double distance = 0.0;
    private long lastNotify;

    private int health;
    private int level;
    private int plugged;
    private boolean present;
    private int scale;
    private int status;
    private String technology;
    private float temperature;
    private float voltage;

    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mContext == null) mContext = context;

        LOGI(TAG, "onReceive action => " + intent.getAction());

        if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
            try {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                present = intent.getExtras().getBoolean(BatteryManager.EXTRA_PRESENT);
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                technology = intent.getExtras().getString(BatteryManager.EXTRA_TECHNOLOGY);
                temperature = ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) / 10;
                voltage = ((float) intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)) / 1000;
            }
            catch (RuntimeException e) {
                e.printStackTrace();
            }
        }

        // On some phones, scale is always 0.
        if (scale == 0) scale = 100;

        if (level > 0) {
            Inspector.setCurrentBatteryLevel(level, scale);

            requestLocationUpdates();

            // Update last known location...
            if (lastKnownLocation == null) {
                lastKnownLocation = LocationInfo.getLastKnownLocation(context);
            }

            Intent service = new Intent(context, DataEstimatorService.class);
            service.putExtra("OriginalAction", intent.getAction());
            service.fillIn(intent, 0);
            service.putExtra("distance", distance);

            // If this broadcasts fires very often move the event post inside the service
            // with constraint batteryLevelChanged
            EventBus.getDefault().post(new BatteryLevelEvent(level));

            startWakefulService(context, service);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (lastKnownLocation != null && location != null) {
            distance = lastKnownLocation.distanceTo(location);
        }
        lastKnownLocation = location;
    }

    @Override
    public void onProviderDisabled(String provider) {
        requestLocationUpdates();
    }

    @Override
    public void onProviderEnabled(String provider) {
        requestLocationUpdates();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        requestLocationUpdates();
    }

    public static Intent getBatteryChangedIntent(final Context context) {
        return context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
    }

    private void requestLocationUpdates() {
        try {
            LocationManager manager = (LocationManager)
                    mContext.getSystemService(Context.LOCATION_SERVICE);
            manager.removeUpdates(this);
            List<LocationProvider> providers = LocationInfo.getEnabledLocationProviders(mContext);
            if (providers != null) {
                for (LocationProvider locationProvider : providers) {
                    manager.requestLocationUpdates(
                            locationProvider.provider,
                            Config.FRESHNESS_TIMEOUT,
                            0,
                            this
                    );
                }
            }
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // Getters & Setters
    public void getCurrentStatus(final Context context, boolean callService) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        assert batteryStatus != null;

        level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
        plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        present = batteryStatus.getExtras().getBoolean(BatteryManager.EXTRA_PRESENT);
        status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        technology = batteryStatus.getExtras().getString(BatteryManager.EXTRA_TECHNOLOGY);
        temperature = (float) (batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10);
        voltage = (float) (batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000);

        /* On some phones, scale is always 0. */
        if (scale == 0)
            scale = 100;
        if (level > 0) {
            Inspector.setCurrentBatteryLevel(level, scale);

            requestLocationUpdates();

            // Update last known location...
            if (lastKnownLocation == null)
                lastKnownLocation = LocationInfo.getLastKnownLocation(context);

            if (callService) {
                Intent service = new Intent(context, DataEstimatorService.class);
                service.putExtra("distance", distance);
                startWakefulService(context, service);
            }
        }
    }

    public String getHealthStatus() {
        String status = "";
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                status = "Unknown";
                break;
            case BatteryManager.BATTERY_HEALTH_GOOD:
                status = "Good";
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                status = "Overheat";
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                status = "Dead";
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                status = "Over Voltage";
                break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                status = "Unspecified Failure";
                break;
        }

        return status;
    }

    public long getLastNotify() {
        return lastNotify;
    }

    public void setLastNotify(long now) {
        this.lastNotify = now;
    }

    public int getHealth() {
        return health;
    }

    public int getLevel() {
        return level;
    }

    public int getPlugged() {
        return plugged;
    }

    public boolean isPresent() {
        return present;
    }

    public int getScale() {
        return scale;
    }

    public int getStatus() {
        return status;
    }

    public String getTechnology() {
        return technology;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getVoltage() {
        return voltage;
    }
}