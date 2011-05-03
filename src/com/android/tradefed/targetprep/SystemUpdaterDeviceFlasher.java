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
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * A {@link IDeviceFlasher} that relies on the system updater to install a
 * system image bundled in a OTA update package. In particular, this
 * implementation doesn't rely on fastboot.
 */
public class SystemUpdaterDeviceFlasher implements IDeviceFlasher {

    private static final String LOG_TAG = "SystemUpdaterDeviceFlasher";

    private ITestsZipInstaller mTestsZipInstaller = new DefaultTestsZipInstaller();

    @SuppressWarnings("unused")
    private UserDataFlashOption mFlashOption = UserDataFlashOption.TESTS_ZIP;

    /**
     * {@inheritDoc}
     * <p>
     * This implementation assumes the device image file returned by
     * {@link IDeviceBuildInfo#getDeviceImageFile()} is an OTA update zip. It's
     * not safe to use this updater in a context where this interpretation
     * doesn't hold.
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    public void flash(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        Log.i(LOG_TAG, String.format("Flashing device %s with build %d", device.getSerialNumber(),
                deviceBuild.getBuildId()));

        // TODO could add a check for bootloader versions and install
        // the one produced by the build server @ the current build if the one
        // on the target is different

        if (installUpdate(device, deviceBuild)) {
            // we only support installing tests from a zip file for now, so no
            // need to for the flash option passed in
            mTestsZipInstaller.pushTestsZipOntoData(device, deviceBuild);
            Log.d(LOG_TAG, "rebooting after installing tests");
            device.reboot();
        }
    }

    private boolean installUpdate(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException, TargetSetupError {
        // FIXME same high level logic as in
        // FastbootDeviceFlasher#checkAndFlashSystem, could be de-duped
        if (device.getBuildId() == deviceBuild.getBuildId()) {
            Log.i(LOG_TAG, String.format("System is already version %d, skipping install",
                    device.getBuildId()));
            // reboot
            return false;
        }
        Log.i(LOG_TAG, String.format("Flashing system %d", deviceBuild.getBuildId()));
        if (!device.pushFile(deviceBuild.getDeviceImageFile(), "/cache/fishtank-ota.zip")) {
            throw new TargetSetupError("Could not push OTA file to the target.");
        }
        String commands =
                "echo --update_package > /cache/recovery/command &&" +
                // FIXME would need to be "CACHE:" instead of "/cache/" for
                // eclair devices
                "echo /cache/fishtank-ota.zip >> /cache/recovery/command";
        device.executeShellCommand(commands);
        device.rebootIntoRecovery();
        device.waitForDeviceAvailable();
        return true;
    }

    // friendly visibility for mock during testing
    void setTestsZipInstaller(ITestsZipInstaller testsZipInstaller) {
        mTestsZipInstaller = testsZipInstaller;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation only supports {@link UserDataFlashOption.TESTS_ZIP}
     * as a valid option
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        if (flashOption != UserDataFlashOption.TESTS_ZIP) {
            throw new IllegalArgumentException(String.format(
                    "%s not supported. This implementation only supports flashing %s", flashOption,
                    UserDataFlashOption.TESTS_ZIP));
        }
        mFlashOption = flashOption;
    }

}