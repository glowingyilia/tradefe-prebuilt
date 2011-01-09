/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.targetsetup;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetsetup.DeviceBuildInfo;
import com.android.tradefed.targetsetup.DeviceSetup;
import com.android.tradefed.targetsetup.IBuildInfo;
import com.android.tradefed.targetsetup.IDeviceFlasher;
import com.android.tradefed.targetsetup.TargetSetupError;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.RunUtil;

/**
 * Functional tests for {@link DeviceSetup}.
 */
public class DeviceSetupFuncTest extends DeviceTestCase {

    private static final String LOG_TAG = "DeviceSetupFuncTest";
    private IDeviceFlasher mMockFlasher;
    private DeviceSetup mDeviceSetup;
    private IDeviceBuildInfo mMockBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockFlasher = new IDeviceFlasher() {

            public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild)
                    throws TargetSetupError {
                // reboot device under test to simulate a flash
                try {
                    device.executeAdbCommand("reboot");
                    // TODO: wait for device to be unavailable instead of sleeping
                    RunUtil.getInstance().sleep(500);
                } catch (DeviceNotAvailableException e) {
                    throw new TargetSetupError("device not avail", e);
                }
            }

            public void setUserDataFlashOption(UserDataFlashOption flashOption) {
                // ignore
            }

        };
        mMockBuildInfo = new DeviceBuildInfo(0, "", "");
        mDeviceSetup = new DeviceSetup() {
            @Override
            protected IDeviceFlasher createFlasher(ITestDevice device) {
                return mMockFlasher;
            }
        };
    }

    /**
     * Simple normal case test for {@link DeviceSetup#setUp(ITestDevice, IBuildInfo)}.
     * <p/>
     * Do setup and verify a few expected properties
     */
    public void testSetup() throws Exception {
        Log.i(LOG_TAG, "testSetup()");

        // reset expected property
        getDevice().executeShellCommand("setprop ro.audio.silent 0");
        mDeviceSetup.setUp(getDevice(), mMockBuildInfo);
        assertTrue(getDevice().executeShellCommand("getprop ro.audio.silent").contains("1"));
        assertTrue(getDevice().executeShellCommand("getprop ro.monkey").contains("1"));
        assertTrue(getDevice().executeShellCommand("getprop ro.test_harness").contains("1"));
        // verify root
        assertTrue(getDevice().executeShellCommand("id").contains("root"));
    }
}
