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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceSelectionOptions;

import org.easymock.EasyMock;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DeviceSelectionMatcher}
 */
public class DeviceSelectionMatcherTest extends TestCase {
    private IDevice mMockDevice;

    // DEVICE_TYPE and OTHER_DEVICE_TYPE should be different
    private static final String DEVICE_TYPE = "charm";
    private static final String OTHER_DEVICE_TYPE = "strange";
    private static final String DEVICE_SERIAL = "12345";
    /**
     * {@inheritDoc}
     */
    protected void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
    }

    public void testGetProductType_mismatchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_mismatchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertFalse(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_matchWithEmptyBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn("");
        EasyMock.expect(mMockDevice.getProperty("ro.product.device")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
    }

    public void testGetProductType_matchWithProperBoard() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty("ro.product.board")).andReturn(DEVICE_TYPE);
        EasyMock.replay(mMockDevice);

        assertTrue(DeviceSelectionMatcher.matches(mMockDevice, options));
    }
}
