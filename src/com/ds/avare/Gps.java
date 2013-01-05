/*
Copyright (c) 2012, Zubair Khan (governer@gmail.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.ds.avare;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * @author zkhan
 *
 */
public class Gps implements LocationListener, android.location.GpsStatus.Listener {

    private Context mContext;
    
    /**
     * App preferences
     */
    private Preferences mPref;

    private int mGpsPeriod;
    
    private GpsInterface mGpsCallback;
    
    /**
     * Time of last GPS message
     */
    private long mGpsLastUpdate;

    /**
     * A timer that clicks to check GPS status
     */
    private Timer mTimer;

    /*
     * GPS manager
     */
    private LocationManager mLocationManager;
        
    private static final int GPS_PERIOD_LONG_MS = 8000;

    /**
     * 
     */
    public Gps(Context ctx, GpsInterface callback) {
        mPref = new Preferences(ctx);
        mContext = ctx;
        mLocationManager = null;
        mTimer = null;
        mGpsCallback = callback;
        if(mPref.isGpsUpdatePeriodShort()) {
            mGpsPeriod = 0;
        }
        else {
            mGpsPeriod = GPS_PERIOD_LONG_MS;
        }
    }

    /**
     * 
     * @return
     */
    public static boolean isGpsAvailable(Context ctx) {
        
        LocationManager lm = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);

        if(null == lm) {
            return false;
        }
        
        List<String> providers = lm.getProviders(true);
        for (int i = providers.size() - 1; i >= 0; i--) {
            if(providers.get(i).equals(LocationManager.GPS_PROVIDER)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 
     * @return
     */
    public static Location getLastLocation(Context ctx) {
        LocationManager lm = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);

        if(null == lm) {
            return null;
        }

        List<String> providers = lm.getProviders(true);

        Location l = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null) {
                break;
            }
        }
        return l;
    }

    /**
     * Must be called to use GPS
     */
    public void start() {

        if(mPref.isGpsUpdatePeriodShort()) {
            mGpsPeriod = 0;
        }
        else {
            mGpsPeriod = GPS_PERIOD_LONG_MS;
        }
        
        /*
         * Start GPS but dont start if already started
         */
        if(null == mLocationManager) {
            mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);

            try {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        mGpsPeriod / 4, 0, this);
                mLocationManager.addGpsStatusListener(this);
                
                /*
                 * Also obtain GSM based locations
                 */
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 
                        0, 0, this);
            }
            catch (Exception e) {
                mLocationManager = null;
            }
        }


        /*
         * Start timer
         */
        if(null == mTimer) {
            mGpsLastUpdate = SystemClock.elapsedRealtime();
            mTimer = new Timer();
            TimerTask gpsTime = new UpdateGps();
            /*
             * Give some delay for check start
             */
            mTimer.scheduleAtFixedRate(gpsTime, (GPS_PERIOD_LONG_MS * 2),
                    GPS_PERIOD_LONG_MS / 4);
        }
    }
    
    /**
     * 
     */
    public void stop() {
        
        /*
         * Stop but dont stop if already stopped
         */
        if(null != mLocationManager) {
            mLocationManager.removeUpdates(this);
            mLocationManager.removeGpsStatusListener(this);
            mLocationManager = null;
            return;
        }

        if(null != mTimer) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    /**
     * 
     * @return
     */
    public static boolean isGpsDisabled(Context ctx, Preferences pref) {
        LocationManager lm = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);

        if(null == lm) {
            return true;
        }
        return(pref.shouldGpsWarn() && 
                (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)));
    }

    /**
     * 
     */
    @Override
    public void onGpsStatusChanged(int event) {
        if(null == mLocationManager) {
            return;
        }
        GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
        if(!mPref.isSimulationMode()) {
            mGpsCallback.statusCallback(gpsStatus);           
        }
    }

    /**
     * 
     */
    @Override
    public void onLocationChanged(Location location) {
        if ((location != null) && (!mPref.isSimulationMode())
                && location.getProvider().equals(LocationManager.GPS_PROVIDER)) {


            synchronized(this) {
                mGpsLastUpdate = SystemClock.elapsedRealtime();
            }

            /*
             * Called by GPS. Update everything driven by GPS.
             */
            mGpsCallback.locationCallback(location);
        }
    }
    
    /**
     * @author zkhan
     *
     */
    
    private class UpdateGps extends TimerTask {
        
        /* (non-Javadoc)
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            
            /*
             * Check when last event was received
             */
            long now = SystemClock.elapsedRealtime();
            long last;
            synchronized(this) {
                last = mGpsLastUpdate;
            }
            if((now - last) > 
                (GPS_PERIOD_LONG_MS * 4)) {
                mGpsCallback.timeoutCallback(true);                
            }
            else {
                mGpsCallback.timeoutCallback(false);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if(provider.equals(LocationManager.GPS_PROVIDER) && (!mPref.isSimulationMode())) {
            mGpsCallback.enabledCallback(false);
        }
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        if(provider.equals(LocationManager.GPS_PROVIDER) && (!mPref.isSimulationMode())) {
            mGpsCallback.enabledCallback(true);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle arg2) {
        if(provider.equals(LocationManager.GPS_PROVIDER) &&
                (status == LocationProvider.OUT_OF_SERVICE)
                && (!mPref.isSimulationMode())) {
            mGpsCallback.statusCallback(null);
        }
    }
}
