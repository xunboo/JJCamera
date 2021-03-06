/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.jjcamera.apps.iosched.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.jjcamera.apps.iosched.BuildConfig;
import com.jjcamera.apps.iosched.Config;
import com.jjcamera.apps.iosched.R;
import com.jjcamera.apps.iosched.settings.SettingsUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

import static com.jjcamera.apps.iosched.util.LogUtils.LOGE;
import static com.jjcamera.apps.iosched.util.LogUtils.LOGW;
import static com.jjcamera.apps.iosched.util.LogUtils.makeLogTag;

public class WiFiUtils {
    // Preference key and values associated with WiFi AP configuration.
    public static final String PREF_WIFI_AP_CONFIG = "pref_wifi_ap_config";
    public static final String WIFI_CONFIG_DONE = "done";
    public static final String WIFI_CONFIG_REQUESTED = "requested";

    private static final String TAG = makeLogTag(WiFiUtils.class);

    public static void installConferenceWiFi(final Context context) {
        // Create conferenceWifiConfig
        WifiConfiguration conferenceWifiConfig = getConferenceWifiConfig();

        // Store conferenceWifiConfig.
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int netId = wifiManager.addNetwork(conferenceWifiConfig);
        if (netId != -1) {
            wifiManager.enableNetwork(netId, false);
            boolean result = wifiManager.saveConfiguration();
            if (!result) {
                Log.e(TAG, "Unknown error while calling WiFiManager.saveConfiguration()");
                Toast.makeText(context,
                        context.getResources().getString(R.string.wifi_install_error_message),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "Unknown error while calling WiFiManager.addNetwork()");
            Toast.makeText(context,
                    context.getResources().getString(R.string.wifi_install_error_message),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void uninstallConferenceWiFi(final Context context) {
        // Create conferenceConfig
        WifiConfiguration conferenceConfig = getConferenceWifiConfig();

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration wifiConfig: configuredNetworks) {
            if (wifiConfig.SSID.equals(conferenceConfig.SSID)) {
                LOGW(TAG, "Removing network: " + wifiConfig.networkId);
                wifiManager.removeNetwork(wifiConfig.networkId);
            }
        }
    }

    /**
     * Helper method to decide whether to bypass conference WiFi setup.  Return true if
     * WiFi AP is already configured (WiFi adapter enabled) or WiFi configuration is complete
     * as per shared preference.
     */
    public static boolean shouldBypassWiFiSetup(final Context context) {
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // Is WiFi on?
        if (wifiManager.isWifiEnabled()) {
            // Check for existing APs.
            final List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            final String conferenceSSID = getConferenceWifiConfig().SSID;
            for(WifiConfiguration config : configs) {
                if (conferenceSSID.equalsIgnoreCase(config.SSID)) return true;
            }
        }

        return WIFI_CONFIG_DONE.equals(getWiFiConfigStatus(context));
    }

    public static boolean isWiFiEnabled(final Context context) {
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    public static boolean isWiFiApConfigured(final Context context) {
        final WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();

        if (configs == null) return false;

        // Check for existing APs.
        final String conferenceSSID = getConferenceWifiConfig().SSID;
        for(WifiConfiguration config : configs) {
            if (conferenceSSID.equalsIgnoreCase(config.SSID)) return true;
        }
        return false;
    }

    // Stored settings_prefs associated with WiFi AP configuration.
    public static String getWiFiConfigStatus(final Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(PREF_WIFI_AP_CONFIG, null);
    }

    public static void setWiFiConfigStatus(final Context context, final String status) {
        if (!WIFI_CONFIG_DONE.equals(status) && !WIFI_CONFIG_REQUESTED.equals(status))
            throw new IllegalArgumentException("Invalid WiFi Config status: " + status);
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putString(PREF_WIFI_AP_CONFIG, status).apply();
    }

    public static boolean installWiFiIfRequested(final Context context) {
        if (WIFI_CONFIG_REQUESTED.equals(getWiFiConfigStatus(context)) && isWiFiEnabled(context)) {
            installConferenceWiFi(context);
            if (isWiFiApConfigured(context)) {
                setWiFiConfigStatus(context, WiFiUtils.WIFI_CONFIG_DONE);
                return true;
            }
        }
        return false;
    }

    public static void showWiFiDialog(Activity activity) {
        FragmentManager fm = activity.getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment prev = fm.findFragmentByTag("dialog_wifi");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        WiFiDialog.newInstance(isWiFiEnabled(activity)).show(ft, "dialog_wifi");
    }

    public static class WiFiDialog extends DialogFragment {
        private static final String ARG_WIFI_ENABLED
                = "com.jjcamera.apps.iosched.ARG_WIFI_ENABLED";

        private boolean mWiFiEnabled;

        public static WiFiDialog newInstance(boolean wiFiEnabled) {
            WiFiDialog wiFiDialogFragment = new WiFiDialog();

            Bundle args = new Bundle();
            args.putBoolean(ARG_WIFI_ENABLED, wiFiEnabled);
            wiFiDialogFragment.setArguments(args);

            return wiFiDialogFragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int padding =
                    getResources().getDimensionPixelSize(R.dimen.content_padding_normal);
            final TextView wifiTextView = new TextView(getActivity());
            int dialogCallToActionText;
            int dialogPositiveButtonText;

            mWiFiEnabled = getArguments().getBoolean(ARG_WIFI_ENABLED);
            if (mWiFiEnabled) {
                dialogCallToActionText = R.string.calltoaction_wifi_configure;
                dialogPositiveButtonText = R.string.wifi_dialog_button_configure;
            } else {
                dialogCallToActionText = R.string.calltoaction_wifi_settings;
                dialogPositiveButtonText = R.string.wifi_dialog_button_settings;
            }
            wifiTextView.setText(Html.fromHtml(getString(R.string.description_setup_wifi_body) +
                    getString(dialogCallToActionText)));
            wifiTextView.setMovementMethod(LinkMovementMethod.getInstance());
            wifiTextView.setPadding(padding, padding, padding, padding);
            final Context context = getActivity();

            return new AlertDialog.Builder(context)
                    .setTitle(R.string.description_configure_wifi)
                    .setView(wifiTextView)
                    .setPositiveButton(dialogPositiveButtonText,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Attempt to configure the Wi-Fi access point.
                                    if (mWiFiEnabled) {
                                        installConferenceWiFi(context);
                                        if (WiFiUtils.isWiFiApConfigured(context)) {
                                            WiFiUtils.setWiFiConfigStatus(
                                                    context,
                                                    WiFiUtils.WIFI_CONFIG_DONE);
                                        }
                                        // Launch Wi-Fi settings screen for user to enable Wi-Fi.
                                    } else {
                                        WiFiUtils.setWiFiConfigStatus(context,
                                                WiFiUtils.WIFI_CONFIG_REQUESTED);
                                        final Intent wifiIntent =
                                                new Intent(Settings.ACTION_WIFI_SETTINGS);
                                        wifiIntent.addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                                        startActivity(wifiIntent);
                                    }
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }
    }

    /**
     * Returns whether we should or should not offer to set up wifi. If asCard == true
     * this will decide whether or not to offer wifi setup actively (as a card, for instance).
     * If asCard == false, this will return whether or not to offer wifi setup passively
     * (in the overflow menu, for instance).
     */
    public static boolean shouldOfferToSetupWifi(final Context context, boolean actively) {
        long now = UIUtils.getCurrentTime(context);
        if (now < Config.WIFI_SETUP_OFFER_START) {
            LOGW(TAG, "Too early to offer wifi");
            return false;
        }
        if (now > Config.CONFERENCE_END_MILLIS) {
            LOGW(TAG, "Too late to offer wifi");
            return false;
        }
        if (!WiFiUtils.isWiFiEnabled(context)) {
            LOGW(TAG, "Wifi isn't enabled");
            return false;
        }
        if (!SettingsUtils.isAttendeeAtVenue(context)) {
            LOGW(TAG, "Attendee isn't onsite so wifi wouldn't matter");
            return false;
        }
        if (WiFiUtils.isWiFiApConfigured(context)) {
            LOGW(TAG, "Attendee is already setup for wifi.");
            return false;
        }
        if (actively && SettingsUtils.hasDeclinedWifiSetup(context)) {
            LOGW(TAG, "Attendee opted out of wifi.");
            return false;
        }
        return true;
    }

    private static WifiConfiguration getConferenceWifiConfig() {
        WifiConfiguration conferenceConfig = new WifiConfiguration();

        // Must be in double quotes to tell system this is an ASCII SSID and passphrase.
        conferenceConfig.SSID = String.format("\"%s\"", BuildConfig.WIFI_SSID);
        conferenceConfig.preSharedKey = String.format("\"%s\"", BuildConfig.WIFI_PASSPHRASE);

        return conferenceConfig;
    }

    public static String getWifiIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = (NetworkInterface)en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress)enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        String ipAddress=inetAddress.getHostAddress().toString();
                        LOGE("IP address  ",ipAddress);
                        return ipAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            LOGE("Socket exception in GetIP Address of Utilities", ex.toString());
        }
        return null;
    }

	public static String[] getMACAddress()  throws Exception
    {
    	InetAddress ia = null;

	    try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = (NetworkInterface)en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress)enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        ia = inetAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            LOGE("Socket exception in GetIP Address of Utilities", ex.toString());
        }

		if( ia == null)		throw new SocketException("null socket") ;
		
        byte[] mac = NetworkInterface.getByInetAddress(ia).getHardwareAddress();

        String[] str_array = new String[2];
        StringBuffer sb1 = new StringBuffer();
        StringBuffer sb2 = new StringBuffer();

        for (int i = 0; i < mac.length; i++)
        {
            if (i != 0)
            {
                sb1.append(":");
            }

            String s = Integer.toHexString(mac[i] & 0xFF);
            sb1.append(s.length() == 1 ? 0 + s : s);
            sb2.append(s.length() == 1 ? 0 + s : s);
        }

        str_array[0] = sb1.toString();
        str_array[1] = sb2.toString();
        return str_array;
        //return sb1.toString().toUpperCase();
    }
}
