package com.transistorsoft.cordova.bggeo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.transistorsoft.locationmanager.adapter.BackgroundGeolocation;
import com.transistorsoft.locationmanager.adapter.callback.TSCallback;
import com.transistorsoft.locationmanager.logger.TSLog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import de.appplant.cordova.plugin.localnotification.TriggerReceiver;
import de.appplant.cordova.plugin.notification.Manager;

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
 * @event http              BackgroundGeolocation.EVENT_HTTP
 *
 */
public class EventReceiver extends BroadcastReceiver {

    private static boolean refreshInProgress = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String eventName = getEventName(intent.getAction());

        String message = TSLog.header("BackgroundGeolocation EventReceiver: " + eventName);
        TSLog.logger.info(message);

        // Decode event name
        if (BackgroundGeolocation.EVENT_HTTP.equalsIgnoreCase(eventName)) {
            onHttp(context, intent);
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
            if (!refreshInProgress) {
                refreshInProgress = true;

                // Get reference to bgGeo Java API
                BackgroundGeolocation bgGeo = BackgroundGeolocation.getInstance(context, intent);
                JSONObject config = bgGeo.getState();
                JSONObject headers = config.getJSONObject("headers");
                JSONObject extras = config.getJSONObject("extras");
                String refreshUrl = extras.getString("refreshUrl");
                JSONObject currentToken = extras.getJSONObject("token");
                String refreshToken = currentToken.getString("refresh_token");

                // Execute an HTTP request to request new auth token
                if (refreshUrl != null && refreshUrl.length() > 0) {
                    JSONObject myNewToken = getNewTokenFromServer(refreshUrl+ "&refresh_token="+refreshToken);
                    refreshInProgress = false;


                    if (myNewToken != null) {
                        String newAccessToken = myNewToken.getString("access_token");
                        String newRefreshToken = myNewToken.getString("refresh_token");
                        // Re-configure BackgroundGeolocation
                        headers.remove("Authorization");
                        headers.put("Authorization", "Bearer " + newAccessToken);

                        String newRefreshUrl = refreshUrl + "&refresh_token="+newRefreshToken;
                        config.put("headers", headers);
                        extras.remove("token");
                        extras.put("token", myNewToken);
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
                        sendNotification(context, intent, "Could not refresh token");
                    }
                } else {
                    TSLog.logger.info(TSLog.error("ConfigUrl not set....."));
                    sendNotification(context, intent, "ConfigUrl not set.....");
                }
            }
        } catch (Exception e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
            refreshInProgress = false;
        }

    }

    private JSONObject getNewTokenFromServer(String refreshUrl) {
        //initialize http connection to layer7
        //return a json of the response
        //use the extras.refreshUrl to post request for new token, replace the refresh token with the one returned -- how to get to storage for the reset of the app?

        try {
            if (refreshUrl == null || refreshUrl.length() == 0) {
                return null;
            }
            JSONObject newToken  = new RefreshTokenTask().execute(refreshUrl).get();
            if (newToken != null) {
                System.out.println("New Token: "+newToken.toString(4));
                newToken.put("lastUpdate", new Date().getTime());
            }
            return newToken;

        } catch (Exception e) {
            TSLog.logger.error(TSLog.error(e.getMessage()));
            return null;
        }
    }

    public void sendNotification(Context context, Intent intent, String msg) {
        //Could just delegate to the local notification plugin assuming it's installed.
        try {
            JSONObject msg2 = new JSONObject("{id:1, title: 'Attention!', text: 'Your login has expired.  Please open the app and log back in.'}");
            Manager.getInstance(context).schedule(msg2, TriggerReceiver.class);
        } catch (Exception e) {

        }


/*
// The id of the channel.
        String CHANNEL_ID = "RAMP";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_fingerprint_error)
                        .setContentTitle("RAMP")
                        .setContentText(msg);

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
// Adds the back stack for the Intent (but not the Intent itself)
//        stackBuilder.addParentStack(ResultActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

// mNotificationId is a unique integer your app uses to identify the
// notification. For example, to cancel the notification, you can pass its ID
// number to NotificationManager.cancel().
        mNotificationManager.notify(999, mBuilder.build());
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
                return new JSONObject(responseMessage);
            } else {
                return null; //could not refresh -- this may be really bad -- gotta login again (send local notification?)
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
