/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.wireless.tests;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

/**
 * Run the WiFi stress tests. This test stresses WiFi soft ap, WiFi scanning
 * and WiFi reconnection in which device switches between cellular and WiFi connection.
 */
public class WifiStressTest implements IRemoteTest, IDeviceTest {
    private ITestDevice mTestDevice = null;

    // Define instrumentation test package and runner.
    private static final String TEST_PACKAGE_NAME = "com.android.connectivitymanagertest";
    private static final String TEST_RUNNER_NAME = ".ConnectivityManagerStressTestRunner";

    private static final Pattern ITERATION_PATTERN =
        Pattern.compile("^iteration (\\d+) out of (\\d+)");

    private String mOutputFile = "WifiStressTestOutput.txt";

    /**
     * Stores the test cases that we should consider running.
     * <p/>
     * This currently consists of "ap", "scanning", and "reconnection" tests.
     */
    private List<TestInfo> mTestList = null;

    private static class TestInfo {
        public String mTestName = null;
        public String mTestClass = null;
        public String mTestMethod = null;
        public String mTestMetricsName = null;
        public RegexTrie<String> mPatternMap = null;

        @Override
        public String toString() {
            return String.format("TestInfo: mTestName(%s), mTestClass(%s), mTestMethod(%s)," +
                    " mTestMetricsName(%s), mPatternMap(%s)", mTestName, mTestClass, mTestMethod,
                    mTestMetricsName, mPatternMap.toString());
        }
    }

    @Option(name="ap-iteration",
            description="The number of iterations to run soft ap stress test")
    private String mApIteration = "100";

    @Option(name="scan-iteration",
            description="The number of iterations to run WiFi scanning test")
    private String mScanIteration = "100";

    @Option(name="reconnect-iteration",
            description="The number of iterations to run WiFi reconnection stress test")
    private String mReconnectionIteration = "100";

    @Option(name="reconnect-ssid",
            description="The ssid for WiFi recoonection stress test")
    private String mReconnectionSsid = "securenetdhcp";

    @Option(name="reconnect-password",
            description="The password for the above ssid in WiFi reconnection stress test")
    private String mReconnectionPassword = "androidwifi";

    @Option(name="idle-time",
            description="The device idle time after screen off")
    private String mIdleTime = "30"; // 30 seconds

    private void setupTests() {
        if (mTestList != null) {
            return;
        }
        mTestList = new ArrayList<TestInfo>(3);

        // Add WiFi AP stress test
        TestInfo t = new TestInfo();
        t.mTestName = "WifiAPStress";
        t.mTestClass = "com.android.connectivitymanagertest.stress.WifiApStress";
        t.mTestMethod = "testWifiHotSpot";
        t.mTestMetricsName = "wifi_stress";
        t.mPatternMap = new RegexTrie<String>();
        t.mPatternMap.put("wifi_ap_stress", ITERATION_PATTERN);
        mTestList.add(t);

        // Add WiFi scanning test
        t = new TestInfo();
        t.mTestName = "WifiScanning";
        t.mTestClass = "com.android.connectivitymanagertest.stress.WifiStressTest";
        t.mTestMethod = "testWifiScanning";
        t.mTestMetricsName = "wifi_scan_performance";
        t.mPatternMap = new RegexTrie<String>();
        t.mPatternMap.put("avg_scan_time", "^average scanning time is (\\d+)");
        t.mPatternMap.put("scan_quality","ssid appear (\\d+) out of (\\d+) scan iterations");
        mTestList.add(t);

        // Add WiFi reconnection test
        t = new TestInfo();
        t.mTestName = "WifiReconnectionStress";
        t.mTestClass = "com.android.connectivitymanagertest.stress.WifiStressTest";
        t.mTestMethod = "testWifiReconnectionAfterSleep";
        t.mTestMetricsName = "wifi_stress";
        t.mPatternMap = new RegexTrie<String>();
        t.mPatternMap.put("wifi_reconnection_stress", ITERATION_PATTERN);
        mTestList.add(t);
    }

    @Override
    public void setDevice(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * Run the Wi-Fi stress test
     * Collect results and post results to dashboard
     */
    @Override
    public void run(ITestInvocationListener standardListener)
            throws DeviceNotAvailableException {
        Assert.assertNotNull(mTestDevice);
        setupTests();
        IRemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(
                TEST_PACKAGE_NAME, TEST_RUNNER_NAME, mTestDevice.getIDevice());
        runner.addInstrumentationArg("softap_iterations", mApIteration);
        runner.addInstrumentationArg("scan_iterations", mScanIteration);
        runner.addInstrumentationArg("reconnect_iterations", mReconnectionIteration);
        runner.addInstrumentationArg("reconnect_ssid", mReconnectionSsid);
        runner.addInstrumentationArg("reconnect_password", mReconnectionPassword);
        runner.addInstrumentationArg("sleep_time", mIdleTime);

        // Add bugreport listener for failed test
        BugreportCollector bugListener = new
            BugreportCollector(standardListener, mTestDevice);
        bugListener.addPredicate(BugreportCollector.AFTER_FAILED_TESTCASES);

        for (TestInfo testCase : mTestList) {
            CLog.d("TestInfo: " + testCase.toString());
            runner.setClassName(testCase.mTestClass);
            runner.setMethodName(testCase.mTestClass, testCase.mTestMethod);
            bugListener.setDescriptiveName(testCase.mTestName);
            mTestDevice.runInstrumentationTests(runner, bugListener);
            logOutputFile(testCase, bugListener);
            cleanOutputFiles();
        }
    }

    /**
     * Collect test results, report test results to dash board.
     *
     * @param test
     * @param listener
     */
    private void logOutputFile(TestInfo test, ITestInvocationListener listener)
        throws DeviceNotAvailableException {
        File resFile = null;
        InputStreamSource outputSource = null;

        try {
            resFile = mTestDevice.pullFileFromExternal(mOutputFile);
            if (resFile == null) {
                throw new TestResultNotAvailableException();
            }
            // Save a copy of the output file
            CLog.d("Sending %d byte file %s into the logosphere!",
                    resFile.length(), resFile);
            outputSource = new SnapshotInputStreamSource(new FileInputStream(resFile));
            listener.testLog(String.format("result-%s.txt", test.mTestName), LogDataType.TEXT,
                    outputSource);

            // Parse the results file and post results to test listener
            parseOutputFile(test, resFile, listener);
        } catch (IOException e) {
            CLog.e("IOException while reading outputfile %s", resFile.getAbsolutePath());
        } catch (TestResultNotAvailableException e) {
            CLog.e("No test result is available, test failed?");
        }
        finally {
            if (resFile != null) {
                resFile.delete();
            }
            if (outputSource != null) {
                outputSource.cancel();
            }
        }
    }

    private void parseOutputFile(TestInfo test, File dataFile,
            ITestInvocationListener listener) {
        Map<String, String> runMetrics = new HashMap<String, String>();
        Map<String, String> runScanMetrics = null;
        boolean isScanningTest = (test.mTestName == "WifiScanning");
        Integer iteration = null;
        try {
            BufferedReader br= new BufferedReader(new FileReader(dataFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                List<List<String>> capture = new ArrayList<List<String>>(1);
                String key = test.mPatternMap.retrieve(capture, line);
                if (key != null) {
                    CLog.d("In output file of test case %s: retrieve key: %s, " +
                            "catpure: %s", test.mTestName, key, capture.toString());
                    //Save results in the metrics
                    if (key == "scan_quality") {
                        // For scanning test, calculate the scan quality
                        int count = Integer.parseInt(capture.get(0).get(0));
                        int total = Integer.parseInt(capture.get(0).get(1));
                        int quality = 0;
                        if (total != 0) {
                            quality = (100 * count) / total;
                        }
                        runMetrics.put(key, Integer.toString(quality));
                    } else {
                        runMetrics.put(key, capture.get(0).get(0));
                    }
                } else {
                    // For scanning test, iterations will also be counted.
                    if (isScanningTest) {
                        Matcher m = ITERATION_PATTERN.matcher(line);
                        if (m.matches()) {
                            iteration = Integer.parseInt(m.group(1));
                        }
                    }
                }
            }
            if (isScanningTest) {
                runScanMetrics = new HashMap<String, String>(1);
                if (iteration == null) {
                    // no matching is found
                    CLog.d("No iteration logs found in %s, set to 0", mOutputFile);
                    iteration = Integer.valueOf(0);
                }
                runScanMetrics.put("wifi_scan_stress", iteration.toString());
            }

            // Report results
            reportMetrics(test.mTestMetricsName, listener, runMetrics);
            if (isScanningTest) {
                reportMetrics("wifi_stress", listener, runScanMetrics);
            }
        } catch (IOException e) {
            CLog.e("IOException while reading from data stream: %s", e);
            return;
        }
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     * <p />
     * Exposed for unit testing
     */
    private void reportMetrics(String metricsName, ITestInvocationListener listener,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics to %s: %s", metricsName, metrics);
        listener.testRunStarted(metricsName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Clean up output files from the last test run
     */
    private void cleanOutputFiles() throws DeviceNotAvailableException {
        CLog.d("Remove output file: %s", mOutputFile);
        String extStore = mTestDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        mTestDevice.executeShellCommand(String.format("rm %s/%s", extStore, mOutputFile));
    }
}
