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
package com.android.tradefed.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@link ITargetPreparer} that installs one or more apps from a
 * {@link IDeviceBuildInfo#getTestsDir()} folder onto device.
 */
@OptionClass(alias = "tests-zip-app")
public class TestAppInstallSetup implements ITargetPreparer {

    private static final String LOG_TAG = "TestAppInstallSetup";

    @Option(name = "test-file-name", description =
        "the name of a test zip file to install on device. Can be repeated.",
        importance = Importance.IF_UNSET)
    private Collection<String> mTestFileNames = new ArrayList<String>();

    @Option(name = AbiFormatter.FORCE_ABI_STRING,
            description = AbiFormatter.FORCE_ABI_DESCRIPTION,
            importance = Importance.IF_UNSET)
    private String mForceAbi = null;

    /**
     * Adds a file to the list of apks to install
     *
     * @param fileName
     */
    public void addTestFileName(String fileName) {
        mTestFileNames.add(fileName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new IllegalArgumentException(String.format("Provided buildInfo is not a %s",
                    IDeviceBuildInfo.class.getCanonicalName()));
        }
        if (mTestFileNames.size() == 0) {
            Log.i(LOG_TAG, "No test apps to install, skipping");
            return;
        }
        File testsDir = ((IDeviceBuildInfo)buildInfo).getTestsDir();
        if (testsDir == null || !testsDir.exists()) {
            throw new TargetSetupError(
                    "Provided buildInfo does not contain a valid tests directory");
        }

        for (String testAppName : mTestFileNames) {
            File testAppFile = FileUtil.getFileForPath(testsDir, "DATA", "app", testAppName);
            if (!testAppFile.exists()) {
                // in addition to /data/app/TestApp.apk
                // also check path like /data/app/TestApp/TestApp.apk
                String[] fields = testAppName.split("\\.");
                testAppFile = FileUtil.getFileForPath(
                        testsDir, "DATA", "app", fields[0], testAppName);
            }
            if (!testAppFile.exists()) {
                throw new TargetSetupError(
                    String.format("Could not find test app %s directory in extracted tests.zip",
                            testAppFile));
            }
            String[] options = {};
            if (mForceAbi != null) {
                String abi = AbiFormatter.getDefaultAbi(device, mForceAbi);
                if (abi != null) {
                    options = new String[]{String.format("--abi %s ", abi)};
                }
            }
            String result = device.installPackage(testAppFile, true, options);
            if (result != null) {
                throw new TargetSetupError(
                        String.format("Failed to install %s on %s. Reason: '%s'", testAppName,
                                device.getSerialNumber(), result));
            }
        }
    }
}
