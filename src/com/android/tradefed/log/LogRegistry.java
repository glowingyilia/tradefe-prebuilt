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
package com.android.tradefed.log;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ConfigurationException;

import java.lang.Thread;
import java.lang.ThreadGroup;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/**
 * A {@link ILogOutput} singleton logger that multiplexes and manages different loggers,
 * using the appropriate one based on the {@link ThreadGroup} of the thread making the call.
 * <p/>
 * Note that the registry hashes on the ThreadGroup in which a thread belongs. If a thread is
 * spawned with its own explicitly-supplied ThreadGroup, it will not inherit the parent thread's
 * logger, and thus will need to register its own logger with the LogRegistry if it wants to log
 * output.
 */
public class LogRegistry implements ILogOutput {
    private static final String LOG_TAG = "LogRegistry";
    private static LogRegistry mLogRegistry = null;
    private Map<ThreadGroup, ILeveledLogOutput> mLogTable =
            new Hashtable<ThreadGroup, ILeveledLogOutput>();
    private FileLogger mGlobalLogger;

    /** the log level to use for log messages occurring on a unregistered thread */
    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;

    /**
     * Package-private constructor; callers should use {@link #getLogRegistry} to get an instance of
     * the {@link LogRegistry}.
     */
    LogRegistry() {
        try {
            mGlobalLogger = new FileLogger(false /* don't delete file on exit */);
            mGlobalLogger.setLogLevelDisplay(DEFAULT_LOG_LEVEL.getStringValue());
        } catch (ConfigurationException e) {
            System.err.println("Failed to create global logger");
            throw new IllegalStateException(e);
        }

    }

    /**
     * Get the {@link LogRegistry} instance
     * <p/>
     *
     * @return a {@link LogRegistry} that can be used to register, get, write to, and close logs
     */
    public static LogRegistry getLogRegistry() {
        if (mLogRegistry == null) {
            mLogRegistry = new LogRegistry();
        }

        return mLogRegistry;
    }

    /**
     * Registers the logger as the instance to use for the current thread.
     *
     */
    public void registerLogger(ILeveledLogOutput log) {
        ILeveledLogOutput oldValue = mLogTable.put(getCurrentThreadGroup(), log);
        if (oldValue != null) {
            Log.e(LOG_TAG, "Registering a new logger when one already exists for this thread!");
            oldValue.closeLog();
        }
    }

    /**
     * Unregisters the current logger in effect for the current thread.
     *
     */
    public void unregisterLogger() {
        ThreadGroup currentThreadGroup = getCurrentThreadGroup();
        if (currentThreadGroup != null) {
            mLogTable.remove(currentThreadGroup);
        }
        else {
          printLog(LogLevel.ERROR, LOG_TAG, "Unregistering when thread has no logger registered.");
        }
    }

    /**
     * Gets the current thread Group.
     *
     * @return the ThreadGroup that the current thread belongs to
     */
    private static ThreadGroup getCurrentThreadGroup() {
        return Thread.currentThread().getThreadGroup();
    }

    /**
     * {@inheritDoc}
     */
    public void printLog(LogLevel logLevel, String tag, String message) {
        ILeveledLogOutput log = mLogTable.get(getCurrentThreadGroup());
        if (log == null) {
            // If there's no logger set for this thread, use global logger
            log = mGlobalLogger;
        }
        LogLevel currentLogLevel = LogLevel.getByString(log.getLogLevel());
        if (logLevel.getPriority() >= currentLogLevel.getPriority()) {
            log.printLog(logLevel, tag, message);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
        printLog(logLevel, tag, message);
    }

    /**
     * Gets the underlying logger associated with this thread.
     *
     * @return the logger for this thread, or null if one has not been registered.
     */
    ILeveledLogOutput getLogger() {
        return mLogTable.get(getCurrentThreadGroup());
    }

    /**
     * Performs any cleanup necessary and closes the associated log for this thread. Note
     * this only closes, and does not unregister the logger from the LogRegistry.
     */
    public void closeLog() {
        ILeveledLogOutput log = mLogTable.get(getCurrentThreadGroup());
        if (log != null) {
            log.closeLog();
        }
    }

    /**
     * Closes and removes all logs being managed by this LogRegistry.
     */
    public void closeAndRemoveAllLogs() {
        Collection<ILeveledLogOutput> allLogs = mLogTable.values();
        Iterator<ILeveledLogOutput> iter = allLogs.iterator();
        while (iter.hasNext()) {
            ILeveledLogOutput log = iter.next();
            log.closeLog();
            iter.remove();
        }
        mGlobalLogger.closeLog();
    }
}