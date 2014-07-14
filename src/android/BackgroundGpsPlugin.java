package com.Dolphin.BgLocation;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class BackgroundGpsPlugin extends CordovaPlugin {
    private static final String TAG = "BackgroundGpsPlugin";

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_SET_CONFIG = "setConfig";

    private Intent updateServiceIntent;
    
    private Boolean isEnabled = false;
    
    private String url;
    private String params;
    private String stationaryRadius = "30";
    private String desiredAccuracy = "100";
    private String distanceFilter = "30";
    private String locationTimeout = "60";
    private String isDebugging = "false";
    private String minAngleDif = "5";
    private String minPeriodInterval = "10";
    private String checkPeriodInterval = "7";
    
    
    
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {
        Activity activity = this.cordova.getActivity();
        Boolean result = false;
        updateServiceIntent = new Intent(activity, LocationUpdateService.class);
        
        if (ACTION_START.equalsIgnoreCase(action) && !isEnabled) {
            result = true;
            if (params == null || url == null) {
                callbackContext.error("Call configure before calling start");
            } else {
                callbackContext.success();
                updateServiceIntent.putExtra("url", url);
                updateServiceIntent.putExtra("params", params);
                updateServiceIntent.putExtra("stationaryRadius", stationaryRadius);
                updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
                updateServiceIntent.putExtra("distanceFilter", distanceFilter);
                updateServiceIntent.putExtra("locationTimeout", locationTimeout);
                updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
                updateServiceIntent.putExtra("isDebugging", isDebugging);
                
                updateServiceIntent.putExtra("minAngleDif", minAngleDif);
                updateServiceIntent.putExtra("minPeriodInterval", minPeriodInterval);
                updateServiceIntent.putExtra("checkPeriodInterval", checkPeriodInterval);
                
                activity.startService(updateServiceIntent);
                
                isEnabled = true;
            }
           
        } else if (ACTION_STOP.equalsIgnoreCase(action)) {
            isEnabled = false;
            result = true;
            activity.stopService(updateServiceIntent);
            callbackContext.success();
        } else if (ACTION_CONFIGURE.equalsIgnoreCase(action)) {
            result = true;
            try {
                // [params, url, stationaryRadius, distanceFilter, locationTimeout, desiredAccuracy, debug, minAngleDif, periodInterval]);
            	Log.i(TAG, "INPUTDATA BEFORe params: "        + data.getString(0));
            	Log.i(TAG, "INPUTDATA BEFORe url: "        + data.getString(1));
            	Log.i(TAG, "INPUTDATA BEFORe stationaryRadius: "        + data.getString(2));
            	Log.i(TAG, "INPUTDATA BEFORe distanceFilter: "        + data.getString(3));
            	Log.i(TAG, "INPUTDATA BEFORe locationTimeout: "        + data.getString(4));
            	Log.i(TAG, "INPUTDATA BEFORe desiredAccuracy: "        + data.getString(5));
            	Log.i(TAG, "INPUTDATA BEFORe isDebugging: "        + data.getString(6));
                Log.i(TAG, "INPUTDATA BEFORe minBearingDiff: "        + data.getString(7));
                Log.i(TAG, "INPUTDATA BEFORe minPeriodInterval: "        + data.getString(8));
                Log.i(TAG, "INPUTDATA BEFORe checkPeriodInterval: "        + data.getString(9));
            	
                this.params = data.getString(0);
                this.url = data.getString(1);
                this.stationaryRadius = data.getString(2);
                this.distanceFilter = data.getString(3);
                this.locationTimeout = data.getString(4);
                this.desiredAccuracy = data.getString(5);
                this.isDebugging = data.getString(6);
                
                this.minAngleDif = data.getString(7);
                this.minPeriodInterval = data.getString(8);
                this.checkPeriodInterval = data.getString(9);

                
            } catch (JSONException e) {
            	Log.i(TAG, "INPUTDATA BEFORe ERROR "        + e);
                callbackContext.error("authToken/url required as parameters: " + e.getMessage());
            }
        } else if (ACTION_SET_CONFIG.equalsIgnoreCase(action)) {
            result = true;
            // TODO reconfigure Service
            callbackContext.success();
        }
        else if ("getLastLocation".equals(action))
        {
        	Location lastLocation = LocationUpdateService.lastLocation;
        	JSONObject location = new JSONObject();
        try {
        	location = new JSONObject(String.format("{lat: %.3f, long:%.3f}", lastLocation.getLatitude(),
        			lastLocation.getLongitude()));
        }
        catch (Throwable e)
        {
        	
        }
        	callbackContext.success(location);
        	
        }

        return result;
    }
}
    
