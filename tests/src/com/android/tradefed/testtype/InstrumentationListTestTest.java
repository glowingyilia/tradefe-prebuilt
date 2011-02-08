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
package com.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InstrumentationListTest}.
 */
public class InstrumentationListTestTest extends TestCase {

    /** The {@link InstrumentationListTest} under test, with all dependencies mocked out */
    private InstrumentationListTest mInstrumentationListTest;

    // The mock objects.
    private ITestDevice mMockTestDevice;
    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * A helper function to build a "list" of InvocationListeners and call the test's run() method
     */
    public void runIListTest() throws DeviceNotAvailableException {
        List<ITestInvocationListener> list = new ArrayList<ITestInvocationListener>(1);
        list.add(mMockListener);
        mInstrumentationListTest.run(list);
    }

    /**
     * Test normal run scenario with a single test.
     */
    @SuppressWarnings("unchecked")
    public void testRun() throws DeviceNotAvailableException {
        final String packageName = "com.foo";
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        final Collection<TestIdentifier> testList = new ArrayList<TestIdentifier>(1);
        testList.add(test);
        final InstrumentationTest mockITest = new InstrumentationTest() {
            @Override
            public void run(List<ITestInvocationListener> listeners) {
                for (ITestInvocationListener listener: listeners) {
                    listener.testRunStarted(packageName, 1);
                    listener.testStarted(test);
                    listener.testEnded(test, Collections.EMPTY_MAP);
                    listener.testRunEnded(0, Collections.EMPTY_MAP);
                }
            }
        };
        mInstrumentationListTest = new InstrumentationListTest(packageName, "foo", testList) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
        };
        mMockListener.testRunStarted(packageName, 1);
        mMockListener.testStarted(test);
        mMockListener.testEnded(test, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationListTest.setDevice(mMockTestDevice);
        runIListTest();
        assertEquals(mMockTestDevice, mockITest.getDevice());
        assertEquals(test.getClassName(), mockITest.getClassName());
        assertEquals(test.getTestName(), mockITest.getMethodName());
        EasyMock.verify(mMockListener, mMockTestDevice);
    }

    /**
     * Test {@link InstrumentationListTest#run(List)} when the test run fails without executing the
     * test.
     */
    @SuppressWarnings("unchecked")
    public void testRun_runFailure() throws DeviceNotAvailableException {
        final String packageName = "com.foo";
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        final String runFailureMsg = "run failed";
        final Collection<TestIdentifier> testList = new ArrayList<TestIdentifier>(1);
        testList.add(test);
        final InstrumentationTest mockITest = new InstrumentationTest() {
            @Override
            public void run(List<ITestInvocationListener> listeners) {
                for (ITestInvocationListener listener: listeners) {
                    listener.testRunStarted(packageName, 1);
                    listener.testRunFailed(runFailureMsg);
                    listener.testRunEnded(0, Collections.EMPTY_MAP);
                }
            }
        };
        mInstrumentationListTest = new InstrumentationListTest(packageName, "foo", testList) {
            @Override
            InstrumentationTest createInstrumentationTest() {
                return mockITest;
            }
        };
        // expect test to be reported as failed
        mMockListener.testRunStarted(packageName, 1);
        mMockListener.testStarted(test);
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(test),
                EasyMock.contains(runFailureMsg));
        mMockListener.testEnded(test, Collections.EMPTY_MAP);
        mMockListener.testRunFailed(runFailureMsg);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        EasyMock.replay(mMockListener, mMockTestDevice);
        mInstrumentationListTest.setDevice(mMockTestDevice);
        runIListTest();
        assertEquals(mMockTestDevice, mockITest.getDevice());
        assertEquals(test.getClassName(), mockITest.getClassName());
        assertEquals(test.getTestName(), mockITest.getMethodName());
        EasyMock.verify(mMockListener, mMockTestDevice);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws DeviceNotAvailableException {
        mInstrumentationListTest = new InstrumentationListTest("foo", "foo",
                new ArrayList<TestIdentifier>());
        mInstrumentationListTest.setDevice(null);
        EasyMock.replay(mMockListener);
        try {
            runIListTest();
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
