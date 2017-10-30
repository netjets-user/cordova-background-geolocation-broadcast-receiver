package com.transistorsoft.cordova.bggeo;

import com.transistorsoft.locationmanager.adapter.BackgroundGeolocation;
import com.transistorsoft.locationmanager.adapter.callback.TSCallback;
import com.transistorsoft.locationmanager.logger.TSLog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * This BroadcastReceiver receives broadcasted events from the BackgroundGeolocation plugin.
 * It's designed for you to customize in your own application, to handle events in the native
 * environment.  You can use this in cases where the user has terminated your foreground Activity
 * while the BackgroundGeolocation background Service continues to operate.
 *
 * You have full access to the BackgroundGeolocation API adapter.  You may execute any documented
 * API method, such as #start, #stop, #changePace, #getCurrentPosition, etc.
 *
 * @author chris scott, Transistor Software www.transistorsoft.com
 *
 * This BroadcastReceiver receives the following events:
 *
 * @event heartbeat         BackgroundGeolocation.EVENT_HEARTBEAT
 * @event motionchange      BackgroundGeolocation.EVENT_MOTIONCHANGE
 * @event location          BackgroundGeolocation.EVENT_LOCATION
 * @event geofence          BackgroundGeolocation.EVENT_GEOFENCE
 * @event http              BackgroundGeolocation.EVENT_HTTP
 * @event schedule          BackgroundGeolocation.EVENT_SCHEDULE
 * @event activitychange    BackgroundGeolocation.EVENT_ACTIVITYCHANGE
 * @event providerchange    BackgroundGeolocation.EVENT_PROVIDERCHANGE
 * @event geofenceschange   BackgroundGeolocation.EVENT_GEOFENCESCHANGE
 * @event heartbeat         BackgroundGeolocation.EVENT_BOOT
 *
 */
public class EventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String eventName = getEventName(intent.getAction());

        String message = TSLog.header("BackgroundGeolocation EventReceiver: " + eventName);
        TSLog.logger.info(message);

        // Decode event name
        if (BackgroundGeolocation.EVENT_HEARTBEAT.equalsIgnoreCase(eventName)) {
            onHeartbeat(context, intent);
        } else if (BackgroundGeolocation.EVENT_MOTIONCHANGE.equalsIgnoreCase(eventName)) {
            onMotionChange(context, intent);
        } else if (BackgroundGeolocation.EVENT_LOCATION.equalsIgnoreCase(eventName)) {
            onLocation(context, intent);
        } else if (BackgroundGeolocation.EVENT_GEOFENCE.equalsIgnoreCase(eventName)) {
            onGeofence(context, intent);
        } else if (BackgroundGeolocation.EVENT_HTTP.equalsIgnoreCase(eventName)) {
            onHttp(context, intent);
        } else if (BackgroundGeolocation.EVENT_SCHEDULE.equalsIgnoreCase(eventName)) {
            onSchedule(context, intent);
        } else if (BackgroundGeolocation.EVENT_ACTIVITYCHANGE.equalsIgnoreCase(eventName)) {
            onActivityChange(context, intent);
        } else if (BackgroundGeolocation.EVENT_PROVIDERCHANGE.equalsIgnoreCase(eventName)) {
            onProviderChange(context, intent);
        } else if (BackgroundGeolocation.EVENT_GEOFENCESCHANGE.equalsIgnoreCase(eventName)) {
            onGeofencesChange(context, intent);
        } else if (BackgroundGeolocation.EVENT_BOOT.equalsIgnoreCase(eventName)) {
            onBoot(context, intent);
        } else if (BackgroundGeolocation.EVENT_TERMINATE.equalsIgnoreCase(eventName)) {
            onTerminate(context, intent);
        }
    }

    /**
     * @event heartbeat
     * @param {Boolean} isMoving
     * @param {JSONObject} location
     */
    private void onHeartbeat(Context context, Intent intent) {
        int status = intent.getIntExtra("status", -1);
        if (status == 401 || status == 403) {
            refreshToken(context, intent);
        }
    }

    /**
     * @event motionchange
     * @param {Boolean} isMoving
     * @param {JSONObject} location
     */
    private void onMotionChange(Context context, Intent intent) {
        boolean isMoving = intent.getBooleanExtra("isMoving", false);
        try {
            JSONObject location = new JSONObject(intent.getStringExtra("location"));
            TSLog.logger.debug(location.toString());
        } catch (JSONException e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }
    }

    /**
     * @event location
     * @param {JSONObject} location
     */
    private void onLocation(Context context, Intent intent) {
        try {
            JSONObject location = new JSONObject(intent.getStringExtra("location"));
            TSLog.logger.debug(location.toString());
        } catch (JSONException e) {
            TSLog.logger.error(e.getMessage());
        }
        int status = intent.getIntExtra("status", -1);
        if (status == 401 || status == 403) {
            refreshToken(context, intent);
        }

    }

    /**
     * @event geofence
     * @param {JSONObject} geofence
     */
    private void onGeofence(Context context, Intent intent) {
        try {
            JSONObject geofenceEvent = new JSONObject(intent.getStringExtra("geofence"));
            String action = geofenceEvent.getString("action");
            String identifier = geofenceEvent.getString("identifier");
            JSONObject location = geofenceEvent.getJSONObject("location");
            if (geofenceEvent.has("extras")) {
                JSONObject extras = geofenceEvent.getJSONObject("extras");
            }
            TSLog.logger.debug(geofenceEvent.toString());
        } catch (JSONException e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }
    }

    /**
     * @event http
     * @param {Integer} status
     * @param {String} responseText
     */
    private void onHttp(Context context, Intent intent) {
        int status = intent.getIntExtra("status", -1);
        if (status == 401 || status == 403) {
            refreshToken(context, intent);
        }
    }

    private void refreshToken(Context context, Intent intent) {
        try {
            // Get reference to bgGeo Java API
            BackgroundGeolocation bgGeo = BackgroundGeolocation.getInstance(context, intent);
            JSONObject config = bgGeo.getState();
            JSONObject headers = config.getJSONObject("headers");
            JSONObject extras = config.getJSONObject("extras");
            String refreshUrl = extras.getString("refreshUrl");

            // Execute an HTTP request to request new auth token
            if (refreshUrl != null && refreshUrl.length() > 0) {
                JSONObject myNewToken = getNewTokenFromServer(refreshUrl);


                if (myNewToken != null) {
                    String newAccessToken = (String)myNewToken.get("access_token");
                    // Re-configure BackgroundGeolocation
                    headers.remove("Authorization");
                    headers.put("Authorization", "Bearer " + newAccessToken);

                    String newRefreshUrl = refreshUrl.substring(0,refreshUrl.indexOf("refresh_token=")+14) + myNewToken.getString("refresh_token");
                    config.put("headers", headers);
                    extras.remove("token");
                    extras.remove("refreshUrl");
                    extras.put("token", myNewToken);
                    extras.put("refreshUrl", newRefreshUrl);
                    config.put("extras", extras);

                    // Same idea as the Javascript API
                    TSCallback bgGeoConfigSuccess = new TSCallback() {
                        @Override
                        public void onSuccess() {
                            System.out.println("BGGeo Config Success");
                            TSLog.logger.info(TSLog.error("BGGeo Config Success"));

                        }

                        @Override
                        public void onFailure(String s) {
                            System.out.println("BGGeo Config Failure");

                        }
                    };
                    bgGeo.setConfig(config, bgGeoConfigSuccess);
                } else {
                    TSLog.logger.info(TSLog.error("Could not refresh token"));
                }
            } else {
                TSLog.logger.info(TSLog.error("ConfigUrl not set....."));
            }

        } catch (Exception e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }

    }

    private JSONObject getNewTokenFromServer(String refreshUrl) {
        //initialize http connection to layer7
//        could we squirl away a few extras in the bgGeo config -- like token endpoint, clicnet id/secret and full token with access/refresh
        //get the last token from ?????
        //post refresh_token request -- gonna need id/secret, url, last token
        //return a json of the response
        //use the extras.refreshUrl to post request for new token, replace the refresh token with the one returned -- how to get to storage for the reset of the app?

        try {
            if (refreshUrl == null || refreshUrl.length() == 0) {
                return null;
            }
            JSONObject newToken  = new RefreshTokenTask().execute(refreshUrl).get();
            System.out.println("New Token: "+newToken.toString(4));
            return newToken;

        } catch (Exception e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
            return null;
        }
    }

    /**
     * @event schedule
     * @param {JSONObject} state
     */
    private void onSchedule(Context context, Intent intent) {
        try {
            JSONObject state = new JSONObject(intent.getStringExtra("state"));
            TSLog.logger.debug(state.toString());
        } catch (JSONException e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }
    }

    /**
     * @event activitychange
     * @param {String} activity
     */
    private void onActivityChange(Context context, Intent intent) {
        String activityName = intent.getStringExtra("activity");
        Integer confidence = intent.getIntExtra("confidence", -1);
        TSLog.logger.debug(activityName + " " + confidence + "%");
//        refreshToken(context, intent);

    }

    /**
     * @event providerchange
     * @param {String} activityName
     */
    private void onProviderChange(Context context, Intent intent) {
        try {
            JSONObject provider = new JSONObject(intent.getStringExtra("provider"));
            TSLog.logger.debug(provider.toString());
        } catch (JSONException e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }
    }

    /**
     * @event geofenceschange
     * @param {JSONArray} on
     * @param {JSONArray} off
     */
    private void onGeofencesChange(Context context, Intent intent) {
        TSLog.logger.debug("geofenceschange: " + intent.getExtras());
        try {
            JSONObject event = new JSONObject(intent.getStringExtra("geofenceschange"));
            JSONArray on = event.getJSONArray("on");
            JSONArray off = event.getJSONArray("off");
            TSLog.logger.debug("on: " + on.toString() + "\noff:" + off.toString());
        } catch (JSONException e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
        }
    }

    /**
     * @event boot
     * @param {JSONObject} state
     */
    private void onBoot(Context context, Intent intent) {
        BackgroundGeolocation adapter = BackgroundGeolocation.getInstance(context, intent);
        JSONObject state = adapter.getState();
    }

    /**
     * @event terminate
     * @param {JSONObject} state
     */
    private void onTerminate(Context context, Intent intent) {
        BackgroundGeolocation adapter = BackgroundGeolocation.getInstance(context, intent);
        JSONObject state = adapter.getState();
/*
        try {
            JSONObject headers = state.getJSONObject("headers");
            String authHeader = headers.getString("Authorization");
            headers.remove("Authorization");
            headers.put("Authorization",authHeader+"zzz");
            adapter.setConfig(state, new TSCallback() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(String s) {

                }
            });
        } catch (Exception e) {}
*/
    }

    /**
     * Fetch the last portion of the Intent action foo.bar.EVENT_NAME -> event_name
     * @param {String} action
     * @return {string} eventName
     */
    private String getEventName(String action) {
        String[] path = action.split("\\.");
        return path[path.length-1].toLowerCase();
    }
}

class RefreshTokenTask extends AsyncTask<String, Integer, JSONObject> {
    protected JSONObject doInBackground(String... refreshUrls) {
        try {
            String responseMessage = sendPost(refreshUrls[0]);
            if (responseMessage != null && responseMessage.length() > 0) {
                JSONObject newToken = new JSONObject(responseMessage);
                return newToken;
            } else {
                return null;
            }

        } catch (Exception e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
            return null;
        }
    }

    private String sendPost(String url) throws Exception {

        URL obj = new URL(url);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
//        con.setRequestProperty("User-Agent", USER_AGENT);
//        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("appagent", "MDSUser");
        con.setRequestProperty("X-Environment", "itg1");

//        String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        TSLog.logger.error(TSLog.error("\nSending 'POST' request to URL : " + url));
        TSLog.logger.error(TSLog.error("Response Code : " + responseCode));

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        String response = "";

        while ((inputLine = in.readLine()) != null) {
            response += inputLine;
        }
        in.close();

        //print result
        System.out.println(response);
        return response;

    }
}