/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tradefed.utils.wifi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A helper class to connect to wifi networks.
 */
public class WifiConnector {

    private static final String TAG = WifiConnector.class.getSimpleName();
    private static final long DEFAULT_TIMEOUT = 30 * 1000;
    private static final long POLL_TIME = 1000;

    private Context mContext;
    private WifiManager mWifiManager;

    /**
     * Thrown when an error occurs while manipulating Wi-Fi services.
     */
    public static class WifiException extends Exception {

        public WifiException(String msg) {
            super(msg);
        }

        public WifiException(String msg, Throwable cause) {
            super(msg, cause);
        }

    }

    public WifiConnector(final Context context) {
        mContext = context;
        mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    }

    private static String quote(String str) {
        return String.format("\"%s\"", str);
    }

    /**
     * Waits until an expected condition is satisfied for DEFAULT_TIMEOUT.
     *
     * @param checker a <code>Callable</code> to check the expected condition
     * @throws WifiException if DEFAULT_TIMEOUT expires
     */
    private void waitForCallable(final Callable<Boolean> checker, final String timeoutMsg)
            throws WifiException {

        long endTime = System.currentTimeMillis() + DEFAULT_TIMEOUT;

        try {
            while (System.currentTimeMillis() < endTime) {
                if (checker.call()) {
                    return;
                }
                Thread.sleep(POLL_TIME);
            }
        } catch (final Exception e) {
            throw new WifiException("failed to wait for callable", e);
        }
        throw new WifiException(timeoutMsg);
    }

    /**
     * Adds a Wi-Fi network configuration.
     *
     * @param ssid SSID of a Wi-Fi network
     * @param psk PSK(Pre-Shared Key) of a Wi-Fi network. This can be null if the given SSID is for
     *            an open network.
     * @return the network ID of a new network configuration
     * @throws WifiException if the operation fails
     */
    public int addNetwork(final String ssid, final String psk) throws WifiException {

        final WifiConfiguration config = new WifiConfiguration();
        // A string SSID _must_ be enclosed in double-quotation marks
        config.SSID = quote(ssid);

        if (psk == null) {
            // KeyMgmt should be NONE only
            final BitSet keymgmt = new BitSet();
            keymgmt.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedKeyManagement = keymgmt;
        } else {
            config.preSharedKey = quote(psk);
        }

        final int networkId = mWifiManager.addNetwork(config);
        if (-1 == networkId) {
            throw new WifiException("failed to add network");
        }

        return networkId;
    }

    /**
     * Removes all Wi-Fi network configurations.
     *
     * @param throwIfFail <code>true</code> if a caller wants an exception to be thrown when the
     *            operation fails. Otherwise <code>false</code>.
     * @throws WifiException if the operation fails
     */
    public void removeAllNetworks(boolean throwIfFail) throws WifiException {
        List<WifiConfiguration> netlist = mWifiManager.getConfiguredNetworks();
        if (netlist != null) {
            int failCount = 0;
            for (WifiConfiguration config : netlist) {
                if (!mWifiManager.removeNetwork(config.networkId)) {
                    Log.w(TAG, String.format("failed to remove network id %d (SSID = %s)",
                            config.networkId, config.SSID));
                    failCount++;
                }
            }
            if (0 < failCount && throwIfFail) {
                throw new WifiException("failed to remove all networks.");
            }
        }
    }

    /**
     * Check network connectivity by sending a HTTP request to a given URL.
     *
     * @param urlToCheck URL to send a test request to
     * @return <code>true</code> if the test request succeeds. Otherwise <code>false</code>.
     */
    public boolean checkConnectivity(final String urlToCheck) {
        final HttpClient httpclient = new DefaultHttpClient();
        try {
            httpclient.execute(new HttpGet(urlToCheck));
        } catch (final IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Connects a device to a given Wi-Fi network and check connectivity.
     *
     * @param ssid SSID of a Wi-Fi network
     * @param psk PSK of a Wi-Fi network
     * @param urlToCheck URL to use when checking connectivity
     * @throws WifiException if the operation fails
     */
    public void connectToNetwork(final String ssid, final String psk, final String urlToCheck)
            throws WifiException {
        if (!mWifiManager.setWifiEnabled(true)) {
            throw new WifiException("failed to enable wifi");
        }

        updateLastNetwork(ssid, psk);

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mWifiManager.isWifiEnabled();
                }
            }, "failed to enable wifi");

        removeAllNetworks(false);

        final int networkId = addNetwork(ssid, psk);
        if (!mWifiManager.enableNetwork(networkId, true)) {
            throw new WifiException(String.format("failed to enable network %s", ssid));
        }
        if (!mWifiManager.saveConfiguration()) {
            throw new WifiException(String.format("failed to save configuration", ssid));
        }

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final SupplicantState state = mWifiManager.getConnectionInfo()
                            .getSupplicantState();
                    return SupplicantState.COMPLETED == state;
                }
            }, String.format("failed to associate to network %s", ssid));

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final WifiInfo info = mWifiManager.getConnectionInfo();
                    return 0 != info.getIpAddress();
                }
            }, String.format("dhcp timeout when connecting to wifi network %s", ssid));

        waitForCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return checkConnectivity(urlToCheck);
                }
            }, String.format("request to %s failed after connecting to wifi network %s",
                    urlToCheck, ssid));
    }

    /**
     * Disconnects a device from Wi-Fi network and disable Wi-Fi.
     *
     * @throws WifiException if the operation fails
     */
    public void disconnectFromNetwork() throws WifiException {
        if (mWifiManager.isWifiEnabled()) {
            removeAllNetworks(false);
            if (!mWifiManager.setWifiEnabled(false)) {
                throw new WifiException("failed to disable wifi");
            }
            waitForCallable(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return !mWifiManager.isWifiEnabled();
                    }
                }, "failed to disable wifi");
        }
    }

    /**
     * Returns Wi-Fi information of a device.
     *
     * @return a {@link JSONObject} containing the current Wi-Fi status
     * @throws WifiException if the operation fails
     */
    public JSONObject getWifiInfo() throws WifiException {
        final JSONObject json = new JSONObject();

        try {
            final WifiInfo info = mWifiManager.getConnectionInfo();
            json.put("ssid", info.getSSID());
            json.put("bssid", info.getBSSID());
            final int addr = info.getIpAddress();
            // IP address is stored with the first octet in the lowest byte
            final int a = (addr >> 0) & 0xff;
            final int b = (addr >> 8) & 0xff;
            final int c = (addr >> 16) & 0xff;
            final int d = (addr >> 24) & 0xff;
            json.put("ipAddress", String.format("%s.%s.%s.%s", a, b, c, d));
            json.put("linkSpeed", info.getLinkSpeed());
            json.put("rssi", info.getRssi());
            json.put("macAddress", info.getMacAddress());
        } catch (final JSONException e) {
            throw new WifiException(e.toString());
        }

        return json;
    }

    /**
     * Reconnects a device to a last connected Wi-Fi network and check connectivity.
     *
     * @param urlToCheck URL to use when checking connectivity
     * @throws WifiException if the operation fails
     */
    public void reconnectToLastNetwork(String urlToCheck) throws WifiException {
        final SharedPreferences prefs = mContext.getSharedPreferences(TAG, 0);
        final String ssid = prefs.getString("ssid", null);
        final String psk = prefs.getString("psk", null);
        if (ssid == null) {
            throw new WifiException("No last connected network.");
        }
        connectToNetwork(ssid, psk, urlToCheck);
    }

    private void updateLastNetwork(final String ssid, final String psk) {
        final SharedPreferences prefs = mContext.getSharedPreferences(TAG, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString("ssid", ssid);
        editor.putString("psk", psk);
        editor.commit();
    }

}
