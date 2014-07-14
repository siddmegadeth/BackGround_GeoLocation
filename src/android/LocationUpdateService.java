package com.Dolphin.BgLocation;
import android.telephony.TelephonyManager;
import android.provider.Settings.Secure;

import java.sql.Date;


import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import com.Dolphin.BgLocation.data.DAOFactory;
import com.Dolphin.BgLocation.data.LocationDAO;

import android.R.string;
import android.annotation.TargetApi;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import static android.telephony.PhoneStateListener.*;
import android.telephony.CellLocation;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.location.Location;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import static java.lang.Math.*;


public class LocationUpdateService extends Service implements LocationListener {
	
    private static final String TAG = "LocationUpdateService";
    private static final String PERIODIC_LOCATIONSENDER_ACTION         = "com.tenforwardconsulting.cordova.bgloc.PERIODIC_LOCATIONSENDER_ACTION";
    private static final String STATIONARY_REGION_ACTION        = "com.tenforwardconsulting.cordova.bgloc.STATIONARY_REGION_ACTION";
    private static final String STATIONARY_ALARM_ACTION         = "com.tenforwardconsulting.cordova.bgloc.STATIONARY_ALARM_ACTION";
    private static final String SINGLE_LOCATION_UPDATE_ACTION   = "com.tenforwardconsulting.cordova.bgloc.SINGLE_LOCATION_UPDATE_ACTION";
    private static final String SINGLE_LOCATION_UPDATE_ACTION2   = "com.tenforwardconsulting.cordova.bgloc.SINGLE_LOCATION_UPDATE_ACTION2";
    private static final String STATIONARY_LOCATION_MONITOR_ACTION = "com.tenforwardconsulting.cordova.bgloc.STATIONARY_LOCATION_MONITOR_ACTION";
    private static final long STATIONARY_TIMEOUT                                = 5 * 1000 * 60;    // 5 minutes.
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_LAZY         = 3 * 1000 * 60;    // 3 minutes.  
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE   = 1 * 1000 * 60;    // 1 minute.
    private static final Integer MAX_STATIONARY_ACQUISITION_ATTEMPTS = 5;
    private static final Integer MAX_SPEED_ACQUISITION_ATTEMPTS = 3;
    
    private PowerManager.WakeLock wakeLock;
    public static Location lastLocation;
    private double lastDifAngle;
    private long lastPositionCheckTime = 0l;
    private long lastUpdateTime = 0l;
    
    
    private JSONObject params;
    private String url = "http://192.168.2.15:3000/users/current_location.json";

    private float stationaryRadius;
    private Location stationaryLocation;
    private PendingIntent periodicLocationPI;
    private PendingIntent stationaryAlarmPI;
    private PendingIntent stationaryLocationPollingPI;
    private long stationaryLocationPollingInterval;
    private PendingIntent stationaryRegionPI;
    private PendingIntent singleUpdatePI;
    private PendingIntent singleUpdatePI2;
    
    private Boolean isMoving = false;
    private Boolean isAcquiringStationaryLocation = false;
    private Boolean isAcquiringSpeed = false;
    private Integer locationAcquisitionAttempts = 0;
    
    private Integer desiredAccuracy = 100;
    private Integer distanceFilter = 30;
    private Integer scaledDistanceFilter;
    
    private float minBearingDiff = 5.00f;
    private long minPeriodInterval = 7 * 1000 ; // 5 seconds.
    private long checkPeriodInterval = 7 * 1000 ; // 7 seconds.
    
    // ARMAN: the minimum time to update the location, the less it is, the quicker we can recieve location updates
    private Integer locationTimeout = 1;
    
    private Boolean isDebugging;

    private ToneGenerator toneGenerator;
    
    private Criteria criteria;
    
    private LocationManager locationManager;
    private AlarmManager alarmManager;
    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;
    public static TelephonyManager telephonyManager = null;
    LocationUpdateService locationUpdateService = this;
   
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        Log.i(TAG, "OnBind" + intent);
        return null;
    }
    
   

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OnCreate");
        
        locationManager         = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        alarmManager            = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        toneGenerator           = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
        connectivityManager     = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        notificationManager     = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        telephonyManager        = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        
        // Stop-detection PI
        stationaryAlarmPI   = PendingIntent.getBroadcast(this, 0, new Intent(STATIONARY_ALARM_ACTION), 0);
        registerReceiver(stationaryAlarmReceiver, new IntentFilter(STATIONARY_ALARM_ACTION));
        
        //ARMAN: Auto-timed location Sender PI
        periodicLocationPI = PendingIntent.getBroadcast(this, 0, new Intent(PERIODIC_LOCATIONSENDER_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        registerReceiver(periodicAlarmReceiver, new IntentFilter(PERIODIC_LOCATIONSENDER_ACTION));
        
        // Stationary region PI
        stationaryRegionPI  = PendingIntent.getBroadcast(this, 0, new Intent(STATIONARY_REGION_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        registerReceiver(stationaryRegionReceiver, new IntentFilter(STATIONARY_REGION_ACTION));
        
        // Stationary location monitor PI
        stationaryLocationPollingPI = PendingIntent.getBroadcast(this, 0, new Intent(STATIONARY_LOCATION_MONITOR_ACTION), 0);
        registerReceiver(stationaryLocationMonitorReceiver, new IntentFilter(STATIONARY_LOCATION_MONITOR_ACTION));
        
        // One-shot PI (TODO currently unused)  
        singleUpdatePI = PendingIntent.getBroadcast(this, 0, new Intent(SINGLE_LOCATION_UPDATE_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        registerReceiver(singleUpdateReceiver, new IntentFilter(SINGLE_LOCATION_UPDATE_ACTION));
        
     // One-shot PI (TODO currently unused)  
        singleUpdatePI2 = PendingIntent.getBroadcast(this, 0, new Intent(SINGLE_LOCATION_UPDATE_ACTION2), 0);
        
        
        ////
        // DISABLED
        // Listen to Cell-tower switches (NOTE does not operate while suspended)
        //telephonyManager.listen(phoneStateListener, LISTEN_CELL_LOCATION);
        //
        
        PowerManager pm         = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        wakeLock.acquire();
        
        // Location criteria
        criteria = new Criteria();
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
        
        
        locationUpdateService = this;
    }
    
    

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        if (intent != null) { 
            try {
                params = new JSONObject(intent.getStringExtra("params"));
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Log.i(TAG, "INPUTDATA minBearingDiff: "        + intent.getStringExtra("minAngleDif"));
            Log.i(TAG, "-INPUTDATA periodicInterval: "        + intent.getStringExtra("periodicInterval"));
            
            Log.i(TAG, "-INPUTDATA distanceFilter: "        + intent.getStringExtra("distanceFilter"));
            
            
            url = intent.getStringExtra("url");
            stationaryRadius = Float.parseFloat(intent.getStringExtra("stationaryRadius"));
            distanceFilter = Integer.parseInt(intent.getStringExtra("distanceFilter"));
            scaledDistanceFilter = distanceFilter;
            desiredAccuracy = Integer.parseInt(intent.getStringExtra("desiredAccuracy"));
            locationTimeout = Integer.parseInt(intent.getStringExtra("locationTimeout"));
            isDebugging = Boolean.parseBoolean(intent.getStringExtra("isDebugging"));
            

            
            minBearingDiff = Float.parseFloat(intent.getStringExtra("minAngleDif"));
            minPeriodInterval = Integer.parseInt(intent.getStringExtra("minPeriodInterval")) * 1000;
            checkPeriodInterval = Integer.parseInt(intent.getStringExtra("checkPeriodInterval")) * 1000;
            
            
            // Build a Notification required for running service in foreground.
            Intent main = new Intent(this, BackgroundGpsPlugin.class);
            main.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, main,  PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder builder = new Notification.Builder(this);
            builder.setContentTitle("ردیابی دستگاه");
            builder.setContentText("فعال");
            builder.setSmallIcon(android.R.drawable.ic_menu_mylocation);
            builder.setContentIntent(pendingIntent);
            Notification notification;
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                notification = buildForegroundNotification(builder);
            } else {
                notification = buildForegroundNotificationCompat(builder);
            }
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
            startForeground(startId, notification);
        }
//        Log.i(TAG, "- url: " + url);
//        Log.i(TAG, "- params: " + params.toString());
//        Log.i(TAG, "- stationaryRadius: "   + stationaryRadius);
//        Log.i(TAG, "- distanceFilter: "     + distanceFilter);
//        Log.i(TAG, "- desiredAccuracy: "    + desiredAccuracy);
//        Log.i(TAG, "- locationTimeout: "    + locationTimeout);
//        Log.i(TAG, "- isDebugging: "        + isDebugging);
        
        
        
        lastLocation = getLastBestLocation();
        lastDifAngle = -900;
        lastPositionCheckTime= lastLocation.getTime();
        
        this.setPace(true);
        
//        mStatusChecker.run();

        
        Log.i(TAG, "ARMAN : Alarm just set");
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() , checkPeriodInterval, periodicLocationPI); 
        
        
        //We want this service to continue running until it is explicitly stopped
        return START_REDELIVER_INTENT;
    }
    
    
      
    @TargetApi(16)
    private Notification buildForegroundNotification(Notification.Builder builder) {
        return builder.build();
    }
    
    @SuppressWarnings("deprecation")
    @TargetApi(15)
    private Notification buildForegroundNotificationCompat(Notification.Builder builder) {
        return builder.getNotification();
    }

    @Override
    public boolean stopService(Intent intent) {
        Log.i(TAG, "- Received stop: " + intent);
        cleanUp();
        if (isDebugging) {
            Toast.makeText(this, "Background location tracking stopped", Toast.LENGTH_SHORT).show();
        }
        return super.stopService(intent);
    }
    
    /**
     * 
     * @param value set true to engage "aggressive", battery-consuming tracking, false for stationary-region tracking
     */
    private void setPace(Boolean value) {
        Log.i(TAG, "setPace: " + value);
        
        Boolean wasMoving   = isMoving;
        isMoving            = value;
        isAcquiringStationaryLocation = false;
        isAcquiringSpeed    = false;
        stationaryLocation  = null;
        
        locationManager.removeUpdates(this);
        
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setHorizontalAccuracy(translateDesiredAccuracy(desiredAccuracy));
        criteria.setPowerRequirement(Criteria.POWER_HIGH);
        
        if (isMoving) {
            // setPace can be called while moving, after distanceFilter has been recalculated.  We don't want to re-acquire velocity in this case.
            if (!wasMoving) {
                isAcquiringSpeed = true;
            }
        } else {
            isAcquiringStationaryLocation = true;
        }

        // Temporarily turn on super-aggressive geolocation on all providers when acquiring velocity or stationary location.
        if (isAcquiringSpeed || isAcquiringStationaryLocation) {
            locationAcquisitionAttempts = 0;
            // Turn on each provider aggressively for a short period of time
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                if (provider != LocationManager.PASSIVE_PROVIDER) { 
                    locationManager.requestLocationUpdates(provider, 0, 0, this);
                }
            }
        } else {
            locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), locationTimeout*1000, scaledDistanceFilter, this);
        }
    }

    /**
    * Translates a number representing desired accuracy of GeoLocation system from set [0, 10, 100, 1000].
    * 0:  most aggressive, most accurate, worst battery drain
    * 1000:  least aggressive, least accurate, best for battery.
    */
    private Integer translateDesiredAccuracy(Integer accuracy) {
        switch (accuracy) {
            case 1000:
                accuracy = Criteria.ACCURACY_LOW;
                break;
            case 100:
                accuracy = Criteria.ACCURACY_MEDIUM;
                break;
            case 10:
                accuracy = Criteria.ACCURACY_HIGH;
                break;
            case 0:
                accuracy = Criteria.ACCURACY_HIGH;
                break;
            default:
                accuracy = Criteria.ACCURACY_MEDIUM;
        }
        return accuracy;
    }

    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or
     * latency a one-off location update is returned via the {@link LocationListener}
     * specified in {@link setChangedLocationListener}.
     * @param minDistance Minimum distance before we require a location update.
     * @param minTime Minimum time required between location updates.
     * @return The most accurate and / or timely previously detected location.
     */
    public Location getLastBestLocation() {
        int minDistance = (int) stationaryRadius;
        long minTime    = System.currentTimeMillis() - (locationTimeout * 1000);
        
        Log.i(TAG, "- fetching last best location " + minDistance + "," + minTime);
        Location bestResult = null;
        
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;

        // Iterate through all the providers on the system, keeping
        // note of the most accurate result within the acceptable time limit.
        // If no result is found within maxTime, return the newest Location.
        List<String> matchingProviders = locationManager.getAllProviders();
        for (String provider: matchingProviders) {
            Log.d(TAG, "- provider: " + provider);
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                Log.d(TAG, " location: " + location.getLatitude() + "," + location.getLongitude() + "," + location.getAccuracy() + "," + location.getSpeed() + "m/s");
                float accuracy = location.getAccuracy();
                long time = location.getTime();
                Log.d(TAG, "time>minTime: " + (time > minTime) + ", accuracy<bestAccuracy: " + (accuracy < bestAccuracy));
//                if ((time > minTime && accuracy < bestAccuracy)) {
            	if ( accuracy < bestAccuracy) {
                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestTime = time;
                }
            }
        }
        return bestResult;
    }

    public void onLocationChanged(Location location) {
        Log.d(TAG, "- onLocationChanged: " + location.getLatitude() + "," + location.getLongitude() + ", accuracy: " + location.getAccuracy() + ", isMoving: " + isMoving + ", speed: " + location.getSpeed());

        
        if (!isMoving && !isAcquiringStationaryLocation && stationaryLocation==null) {
            // Perhaps our GPS signal was interupted, re-acquire a stationaryLocation now.
            setPace(false);
        }
        
        if (isDebugging) {
//            Toast.makeText(this, "mv:"+isMoving+",acy:"+location.getAccuracy()+",v:"+location.getSpeed()+",df:"+scaledDistanceFilter +"bearing:" + location.getBearing(), Toast.LENGTH_LONG).show();
        }
        if (isAcquiringStationaryLocation) {
            if (stationaryLocation == null || stationaryLocation.getAccuracy() > location.getAccuracy()) {
                stationaryLocation = location;
            }
            if (++locationAcquisitionAttempts == MAX_STATIONARY_ACQUISITION_ATTEMPTS) {
                isAcquiringStationaryLocation = false;
                startMonitoringStationaryRegion(stationaryLocation);
                if (isDebugging) {
                    startTone("long_beep");
                }
            } else {
                // Unacceptable stationary-location: bail-out and wait for another.
                if (isDebugging) {
                    startTone("beep");
                }
                return;
            }
        } else if (isAcquiringSpeed) {
            if (++locationAcquisitionAttempts == MAX_SPEED_ACQUISITION_ATTEMPTS) {
                // Got enough samples, assume we're confident in reported speed now.  Play "woohoo" sound.
                if (isDebugging) {
                    startTone("doodly_doo");
                }
                isAcquiringSpeed = false;
                scaledDistanceFilter = calculateDistanceFilter(location.getSpeed());
                setPace(true);
            } else {
                if (isDebugging) {
                    startTone("beep");
                }
                return;
            }
        } else if (isMoving) {
            if (isDebugging) {
                startTone("beep");
            }
            // Only reset stationaryAlarm when accurate speed is detected, prevents spurious locations from resetting when stopped.
            if ( (location.getSpeed() >= 1) && (location.getAccuracy() <= stationaryRadius) ) {
                resetStationaryAlarm();
            }
            // Calculate latest distanceFilter, if it changed by 5 m/s, we'll reconfigure our pace.
            Integer newDistanceFilter = calculateDistanceFilter(location.getSpeed());
            if (newDistanceFilter != scaledDistanceFilter.intValue()) {
                Log.i(TAG, "- updated distanceFilter, new: " + newDistanceFilter + ", old: " + scaledDistanceFilter);
                scaledDistanceFilter = newDistanceFilter;
                setPace(true);
            }
            if (location.distanceTo(lastLocation) < distanceFilter) {
                return;
            }
        } else if (stationaryLocation != null) {
            return;
        }
        // Go ahead and cache, push to server
        lastLocation = location;
        persistLocation(location);

        if (this.isNetworkConnected()) {
            Log.d(TAG, "Scheduling location network post");
            schedulePostLocations();
        } else {
            Log.d(TAG, "Network unavailable, waiting for now");
        }
    }
    
    /**
     * Plays debug sound
     * @param name
     */
    private void startTone(String name) {
        int tone = 0;
        int duration = 1000;
        
        if (name.equals("beep")) {
            tone = ToneGenerator.TONE_PROP_BEEP;
        } else if (name.equals("beep_beep_beep")) {
            tone = ToneGenerator.TONE_CDMA_CONFIRM;
        } else if (name.equals("long_beep")) {
            tone = ToneGenerator.TONE_CDMA_ABBR_ALERT;
        } else if (name.equals("doodly_doo")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE;
        } else if (name.equals("chirp_chirp_chirp")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
        } else if (name.equals("dialtone")) {
            tone = ToneGenerator.TONE_SUP_RINGTONE;
        }
        toneGenerator.startTone(tone, duration);
    }
    
    public void resetStationaryAlarm() {
        alarmManager.cancel(stationaryAlarmPI);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + STATIONARY_TIMEOUT, stationaryAlarmPI); // Millisec * Second * Minute
    }

    private Integer calculateDistanceFilter(Float speed) {
        Double newDistanceFilter = (double) distanceFilter;
        if (speed < 100) {
            float roundedDistanceFilter = (round(speed / 5) * 5);
            newDistanceFilter = pow(roundedDistanceFilter, 2) + (double) distanceFilter;
        }
        return (newDistanceFilter.intValue() < 1000) ? newDistanceFilter.intValue() : 1000;
    }

    private void startMonitoringStationaryRegion(Location location) {
        locationManager.removeUpdates(this);
        stationaryLocation = location;
        
        Log.i(TAG, "- startMonitoringStationaryRegion (" + location.getLatitude() + "," + location.getLongitude() + "), accuracy:" + location.getAccuracy());

        // Here be the execution of the stationary region monitor
        locationManager.addProximityAlert(
                location.getLatitude(),
                location.getLongitude(),
                (location.getAccuracy() < stationaryRadius) ? stationaryRadius : location.getAccuracy(),
                (long)-1,
                stationaryRegionPI
        );
        
        startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_LAZY);
    }
    
    public void startPollingStationaryLocation(long interval) {
        // proximity-alerts don't seem to work while suspended in latest Android 4.42 (works in 4.03).  Have to use AlarmManager to sample
        //  location at regular intervals with a one-shot.
        stationaryLocationPollingInterval = interval;
        alarmManager.cancel(stationaryLocationPollingPI);
        long start = System.currentTimeMillis() + (60 * 1000);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, start, interval, stationaryLocationPollingPI);
    }
    
    public void onPollStationaryLocation(Location location) {
        if (isMoving) {
            return;
        }
        if (isDebugging) {
            startTone("beep");
        }
	float distance = abs(location.distanceTo(stationaryLocation) - stationaryLocation.getAccuracy() - location.getAccuracy());
        
        if (isDebugging) {
            Toast.makeText(this, "Stationary exit in " + (stationaryRadius-distance) + "m", Toast.LENGTH_LONG).show();
        }
        
        // TODO http://www.cse.buffalo.edu/~demirbas/publications/proximity.pdf
        // determine if we're almost out of stationary-distance and increase monitoring-rate.
        Log.i(TAG, "- distance from stationary location: " + distance);
        if (distance > stationaryRadius) {
            onExitStationaryRegion(location);
        } else if (distance > 0) {
            startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE);
        } else if (stationaryLocationPollingInterval != STATIONARY_LOCATION_POLLING_INTERVAL_LAZY) {
            startPollingStationaryLocation(STATIONARY_LOCATION_POLLING_INTERVAL_LAZY);
        }
    }
    /**
    * User has exit his stationary region!  Initiate aggressive geolocation!
    */
    public void onExitStationaryRegion(Location location) {
        // Filter-out spurious region-exits:  must have at least a little speed to move out of stationary-region
        if (isDebugging) {
            startTone("beep_beep_beep");
        }
        // Cancel the periodic stationary location monitor alarm.
        alarmManager.cancel(stationaryLocationPollingPI);
        
        // Kill the current region-monitor we just walked out of.
        locationManager.removeProximityAlert(stationaryRegionPI);
        
        // Engage aggressive tracking.
        this.setPace(true);
    }
    
    /**
    * TODO Experimental cell-tower change system; something like ios significant changes.
    */
    public void onCellLocationChange(CellLocation cellLocation) {
        Log.i(TAG, "- onCellLocationChange" + cellLocation.toString());
        if (isDebugging) {
            Toast.makeText(this, "Cellular location change", Toast.LENGTH_LONG).show();
            startTone("chirp_chirp_chirp");
        }
        if (!isMoving && stationaryLocation != null) {
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            locationManager.requestSingleUpdate(criteria, singleUpdatePI);
        }
    }

    /**
    * Broadcast receiver for receiving a single-update from LocationManager.
    */
    private BroadcastReceiver singleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = LocationManager.KEY_LOCATION_CHANGED;
            Location location = (Location)intent.getExtras().get(key);
            if (location != null) {
                Log.d(TAG, "singleUpdateReciever" + location.toString());
                onPollStationaryLocation(location);

            }
            else
            {
            	Log.d(TAG, "singleUpdateReciever return NULL" );
            }
        }
    };
    private BroadcastReceiver singleUpdateReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.d(TAG, "ARMAN: singleUpdateReciever fired" );
            String key = LocationManager.KEY_LOCATION_CHANGED;
            Location location = (Location)intent.getExtras().get(key);
            if (location != null) {
                Log.d(TAG, "ARMAN: singleUpdateReciever " + location.toString());

                if (-900 == lastDifAngle)
                {
                	lastDifAngle = 0;
                }
                
                
                if ((location.getTime() - lastPositionCheckTime > minPeriodInterval) ||
                		(Math.abs(lastLocation.bearingTo(location) - lastDifAngle) > minBearingDiff) 
                		|| (lastLocation.distanceTo(location) > distanceFilter))
                {
                	createToast(String.format("Bearing Dif:%.3f,DifTime:%d, Distance:%.3f", Math.abs(lastLocation.bearingTo(location) - lastDifAngle),
                			((location.getTime() - lastPositionCheckTime) /1000), lastLocation.distanceTo(location)));
                	
                	
                	Log.d(TAG, "ARMAN: Post Location,periodicInterval: " + minPeriodInterval + "Called time passed:" + ((location.getTime() - lastPositionCheckTime) /1000) );
	                locationUpdatePoster(location);
                }
                
                lastDifAngle = lastLocation.bearingTo(location);
                lastLocation = location;
                lastPositionCheckTime = location.getTime();

            }
            else
            {
            	Log.d(TAG, "ARMAN: singleUpdateReciever return NULL" );
            }
            
            unregisterReceiver(singleUpdateReceiver2);
        }
    };
    
    /**
    * Broadcast receiver which detcts a user has stopped for a long enough time to be determined as STOPPED
    */
    private BroadcastReceiver stationaryAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i(TAG, "- stationaryAlarm fired");
            setPace(false);
        }
    };
    /**
    * Broadcast receiver which sends location to server, after a period of time, no matter if it's stopped or moving.
    */
    private BroadcastReceiver periodicAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
        	criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            
            registerReceiver(singleUpdateReceiver2, new IntentFilter(SINGLE_LOCATION_UPDATE_ACTION2));
            Log.i(TAG, "ARMAN: REQUESTED SINGLE UPDATE");
//            createToast("REQUESTED SINGLE UPDATE");
//            locationManager.requestSingleUpdate(criteria, singleUpdatePI2);
            locationManager.requestLocationUpdates(locationManager.GPS_PROVIDER, 0, 0, singleUpdatePI2);
        }
        
    };
    private void locationUpdatePoster(Location location)
    {
    	Log.i(TAG, "ARMAN: FIRED periodicAlarmReceiver -> Forcing app to update location in Runnable");
    	Location l2 = getLastBestLocation();
    	if (null != location)
    		l2 = location;
    	
    	if (null != l2) {
        	com.Dolphin.BgLocation.data.Location l = com.Dolphin.BgLocation.data.Location.fromAndroidLocation(l2);
        	
        	Log.d(TAG, " ARMAN: Location in periodicAlarmReceiver location: " + l.getLatitude() + "," + l.getLongitude() + "," + l.getAccuracy() + "," + l.getSpeed() + "m/s");

        	persistLocation(l2);
        	// here is what i Do, If there is a network availabe, post the location to server normally at the specified period
        	// otherwise, persist the location to db and wait for network availabilty
            if (locationUpdateService.isNetworkConnected()) {
            	schedulePostLocations();
            	//PostLocationAsync task = new LocationUpdateService.PostLocationAsync();

//	            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
//	              //  task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, l2);
//	            else
	               // task.execute();
//	            createToast("Location posted to server, bearing" + l2.getBearing());
            } else {
//            	persistLocation(l2);
                Log.d(TAG, "Network unavailable, waiting for now");
            }

    	}
    }
    private void createToast(String msg)
    {
    	if (isDebugging) {
            Toast.makeText(locationUpdateService.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Broadcast receiver to handle stationaryMonitor alarm, fired at low frequency while monitoring stationary-region.
     * This is required because latest Android proximity-alerts don't seem to operate while suspended.  Regularly polling
     * the location seems to trigger the proximity-alerts while suspended.
     */
     private BroadcastReceiver stationaryLocationMonitorReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent)
         {
             Log.i(TAG, "- stationaryLocationMonitorReceiver fired");
             if (isDebugging) {
                 startTone("dialtone");
             }
             criteria.setAccuracy(Criteria.ACCURACY_FINE);
             criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
             criteria.setPowerRequirement(Criteria.POWER_HIGH);
             locationManager.requestSingleUpdate(criteria, singleUpdatePI);
         }
     };
    /**
    * Broadcast receiver which detects a user has exit his circular stationary-region determined by the greater of stationaryLocation.getAccuracy() OR stationaryRadius
    */
    private BroadcastReceiver stationaryRegionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "stationaryRegionReceiver");
            String key = LocationManager.KEY_PROXIMITY_ENTERING;

            Boolean entering = intent.getBooleanExtra(key, false);
            if (entering) {
                Log.d(TAG, "- ENTER");
                if (isMoving) {
                    setPace(false);
                }
            }
            else {
                Log.d(TAG, "- EXIT");
                // There MUST be a valid, recent location if this event-handler was called.
                Location location = getLastBestLocation();
                if (location != null) {
                    onExitStationaryRegion(location);
                }
            }
        }
    };
    /**
    * TODO Experimental, hoping to implement some sort of "significant changes" system here like ios based upon cell-tower changes.
    */
    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCellLocationChanged(CellLocation location)
        {
            onCellLocationChange(location);
        }
    };

    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onProviderDisabled: " + provider);
    }
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onProviderEnabled: " + provider);
    }
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onStatusChanged: " + provider + ", status: " + status);
    }
    private void schedulePostLocations() {
        PostLocationTask task = new LocationUpdateService.PostLocationTask();
        Log.d(TAG, "beforeexecute " +  task.getStatus());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        else
            task.execute();
        Log.d(TAG, "afterexecute " +  task.getStatus());
    }
    

    private boolean postLocation(com.Dolphin.BgLocation.data.Location l) {
        if (l == null) {
            Log.w(TAG, "postLocation: null location");
            return false;
        }
        try {
            lastUpdateTime = SystemClock.elapsedRealtime();
            Log.i(TAG, "Posting  native location update: " + l);
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpPost request = new HttpPost(url);

            JSONObject location = new JSONObject();
            location.put("latitude", l.getLatitude());
            location.put("longitude", l.getLongitude());
            location.put("accuracy", l.getAccuracy());
            location.put("speed", l.getSpeed());
            location.put("recorded_at", l.getRecordedAt());
            params.put("location", location);
            float [] result = new float[1];
            
            
            Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
    				Double.valueOf( l.getLatitude() ), Double.valueOf( l.getLongitude() ),result);
            
           
            
        //    Location_Socket socket= new Location_Socket("", 0);
           /// TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);//(TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            TelephonyManager  imei = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
            //String imei=telephonyManager.getDeviceId();
           
            String message= "$$" + imei.getDeviceId() + ",00," + l.getRecordedAt() +","+ l.getLatitude()+"," + l.getLongitude() +","
            		+ l.getAltitude() + "," + l.getSpeed() + "," + result[0] + "," + l.getBearing();
            Log.i(TAG, "socket : " + message);
            
            
            StringEntity se = new StringEntity(params.toString());
            request.setEntity(se);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");
            Log.d(TAG, "Posting to " + request.getURI().toString());
            HttpResponse response = httpClient.execute(request);
            Log.i(TAG, "Response received: " + response.getStatusLine());
            
            if (response.getStatusLine().getStatusCode() == 200) {
                return true;
            } else {
                return false;
            }
        } catch (Throwable e) {
            Log.w(TAG, " Exception posting location: " + e);
            e.printStackTrace();
            return false;
        }
    }
    private void persistLocation(Location location) {
        LocationDAO dao = DAOFactory.createLocationDAO(this.getApplicationContext());
        com.Dolphin.BgLocation.data.Location savedLocation = com.Dolphin.BgLocation.data.Location.fromAndroidLocation(location);

        if (dao.persistLocation(savedLocation)) {
            Log.d(TAG, "Persisted Location: " + savedLocation);
        } else {
            Log.w(TAG, "Failed to persist location");
        }
    }

    private boolean isNetworkConnected() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            Log.d(TAG, "Network found, type = " + networkInfo.getTypeName());
            return networkInfo.isConnected();
        } else {
            Log.d(TAG, "No active network info");
            return false;
        }
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "------------------------------------------ Destroyed Location update Service");
        cleanUp();
        super.onDestroy();
        
//        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
//        Intent intent = new Intent(this, LocationUpdateService.class);
//        
//        intent.putExtra("url", url);
//        intent.putExtra("stationaryRadius", stationaryRadius);
//        intent.putExtra("distanceFilter", distanceFilter);
//        intent.putExtra("desiredAccuracy", desiredAccuracy);
//        intent.putExtra("locationTimeout", locationTimeout);
//        intent.putExtra("isDebugging", isDebugging);
//
//        alarm.set(
//            alarm.RTC_WAKEUP,
//            System.currentTimeMillis() + (1000 * 10 ),
//            PendingIntent.getService(this, 0, intent , 0)
//        );
    }
    private void cleanUp() {
        locationManager.removeUpdates(this);
        alarmManager.cancel(stationaryAlarmPI);
        alarmManager.cancel(stationaryLocationPollingPI);
        toneGenerator.release();
        
        unregisterReceiver(stationaryAlarmReceiver);
        unregisterReceiver(singleUpdateReceiver);
        unregisterReceiver(stationaryRegionReceiver);
        unregisterReceiver(stationaryLocationMonitorReceiver);
        
        unregisterReceiver(periodicAlarmReceiver);
        
        try {
            unregisterReceiver(singleUpdateReceiver2);
        }
        catch(Throwable e){}
        
        if (stationaryLocation != null && !isMoving) {
            try {
                locationManager.removeProximityAlert(stationaryRegionPI);
            } catch (Throwable e) {
                Log.w(TAG, "- Something bad happened while removing proximity-alert");
            }
        }
        stopForeground(true);
        wakeLock.release();
        
        
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        this.stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private class PostLocationTask extends AsyncTask<Object, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Object...objects) {
            Log.d(TAG, "Executing PostLocationTask#doInBackground");
            LocationDAO locationDAO = DAOFactory.createLocationDAO(LocationUpdateService.this.getApplicationContext());
            for (com.Dolphin.BgLocation.data.Location savedLocation : locationDAO.getAllLocations()) {
                Log.d(TAG, "Posting saved location");
                if (postLocation(savedLocation)) {
                    locationDAO.deleteLocation(savedLocation);
                }
            }
            return true;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, "PostLocationTask#onPostExecture");
        }
    }
//    private class PostLocationAsync extends AsyncTask<Location, Integer, Boolean> {
//
//        @Override
//        protected Boolean doInBackground(Location...locations) {
//            Log.d(TAG, "ARMAN: Executing PostLocationASYNC#doInBackground");
//            for (Location l : locations)
//            {
//
//	            try {
//	                DefaultHttpClient httpClient = new DefaultHttpClient();
//	                HttpPost request = new HttpPost(url);
//	
//	                JSONObject location = new JSONObject();
//	                location.put("latitude", l.getLatitude());
//	                location.put("longitude", l.getLongitude());
//	                location.put("accuracy", l.getAccuracy());
//	                location.put("speed", l.getSpeed());
//	                location.put("recorded_at", l.getTime());
//	                params.put("location", location);
//	
//	                StringEntity se = new StringEntity(params.toString());
//	                request.setEntity(se);
//	                request.setHeader("Accept", "application/json");
//	                request.setHeader("Content-type", "application/json");
//	                Log.d(TAG, "ARMAN: Posting to " + request.getURI().toString());
//	                HttpResponse response = httpClient.execute(request);
//	                Log.i(TAG, "ARMAN: Response received: " + response.getStatusLine());
//	                if (response.getStatusLine().getStatusCode() == 200) {
//	                	
//	                    return true;
//	                } else {
//	                	persistLocation(l);
//	                    return false;
//	                }
//	            } catch (Throwable e) {
//	                Log.w(TAG, "ARMAN: Exception posting location: " + e);
//	                e.printStackTrace();
//	                return false;
//  
//	            }
//	            
//            }
//            return true;
//        }
//        
//        @Override
//        protected void onPostExecute(Boolean result) {
////            Log.d(TAG, "ARMAN: PostLocationASYNC#onPostExecture");
//            
//        }
//    }
}
