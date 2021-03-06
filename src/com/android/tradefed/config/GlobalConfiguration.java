/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tradefed.config;

import com.android.tradefed.command.CommandScheduler;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.device.DeviceManager;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.log.ITerribleFailureHandler;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.MultiMap;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IGlobalConfiguration} implementation that stores the loaded config objects in a map
 */
public class GlobalConfiguration implements IGlobalConfiguration {
    // type names for built in configuration objects
    public static final String DEVICE_MONITOR_TYPE_NAME = "device_monitor";
    public static final String DEVICE_MANAGER_TYPE_NAME = "device_manager";
    public static final String WTF_HANDLER_TYPE_NAME = "wtf_handler";
    public static final String HOST_OPTIONS_TYPE_NAME = "host_options";
    public static final String DEVICE_REQUIREMENTS_TYPE_NAME = "device_requirements";
    public static final String SCHEDULER_TYPE_NAME = "command_scheduler";

    private static Map<String, ObjTypeInfo> sObjTypeMap = null;
    private static IGlobalConfiguration sInstance = null;
    private static final Object sInstanceLock = new Object();

    private static final String GLOBAL_CONFIG_VARIABLE = "TF_GLOBAL_CONFIG";
    private static final String GLOBAL_CONFIG_FILENAME = "tf_global_config.xml";

    /** Mapping of config object type name to config objects. */
    private Map<String, List<Object>> mConfigMap;
    private MultiMap<String, String> mOptionMap;
    private final String mName;
    private final String mDescription;

    /**
     * Returns a reference to the singleton {@link GlobalConfiguration} instance for this TF
     * instance.
     *
     * @throws IllegalStateException if {@see createGlobalConfiguration(String[])} has not already
     *         been called.
     */
    public static IGlobalConfiguration getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("GlobalConfiguration has not yet been initialized!");
        }
        return sInstance;
    }

    /**
     * Returns a reference to the singleton {@link DeviceManager} instance for this TF
     * instance.
     *
     * @throws IllegalStateException if {@see createGlobalConfiguration(String[])} has not already
     *         been called.
     */
    public static IDeviceManager getDeviceManagerInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("GlobalConfiguration has not yet been initialized!");
        }
        return sInstance.getDeviceManager();
    }

    /**
     * Sets up the {@link GlobalConfiguration} singleton for this TF instance.  Must be called
     * once and only once, before anything attempts to call {@see getInstance()}
     *
     * @throws IllegalStateException if called more than once
     */
    public static List<String> createGlobalConfiguration(String[] args)
            throws ConfigurationException {
        synchronized (sInstanceLock) {
            if (sInstance != null) {
                throw new IllegalStateException("GlobalConfiguration is already initialized!");
            }

            List<String> nonGlobalArgs = new ArrayList<String>(args.length);
            IConfigurationFactory configFactory = ConfigurationFactory.getInstance();
            String globalConfigPath = getGlobalConfigPath();

            if (globalConfigPath != null) {
                // Found a global config file; attempt to parse and use it
                sInstance = configFactory.createGlobalConfigurationFromArgs(
                        ArrayUtil.buildArray(new String[] {globalConfigPath}, args), nonGlobalArgs);
                System.err.format("Success!  Using global config \"%s\"\n", globalConfigPath);
            } else {
                // Use default global config
                sInstance = new GlobalConfiguration();
                nonGlobalArgs = Arrays.asList(args);
            }
            return nonGlobalArgs;
        }
    }

    /**
     * Returns the path to a global config, if one exists, or <code>null</code> if none could be
     * found.
     * <p />
     * Search locations, in decreasing order of precedence
     * <ol>
     *   <li><code>$TF_GLOBAL_CONFIG</code> environment variable</li>
     *   <li><code>tf_global_config.xml</code> file in $PWD</li>
     *   <li>(FIXME) <code>tf_global_config.xml</code> file in dir where <code>tradefed.sh</code>
     *       lives</li>
     * </ol>
     */
    private static String getGlobalConfigPath() throws ConfigurationException {
        String path = System.getenv(GLOBAL_CONFIG_VARIABLE);
        if (path != null) {
            // don't actually check for accessibility here, since the variable might be specifying
            // a java resource rather than a filename.  Even so, this can help the user figure out
            // which global config (if any) was picked up by TF.
            System.err.format("Attempting to use global config \"%s\" from variable $%s.\n",
                    path, GLOBAL_CONFIG_VARIABLE);
            return path;
        }

        File file = new File(GLOBAL_CONFIG_FILENAME);
        if (file.exists()) {
            path = file.getPath();
            System.err.format("Attempting to use autodetected global config \"%s\".\n", path);
            return path;
        }

        // FIXME: search in tradefed.sh launch dir (or classpath?)

        return null;
    }

    /**
     * Container struct for built-in config object type
     */
    private static class ObjTypeInfo {
        final Class<?> mExpectedType;
        /** true if a list (ie many objects in a single config) are supported for this type */
        final boolean mIsListSupported;

        ObjTypeInfo(Class<?> expectedType, boolean isList) {
            mExpectedType = expectedType;
            mIsListSupported = isList;
        }
    }

    /**
     * Determine if given config object type name is a built in object
     *
     * @param typeName the config object type name
     * @return <code>true</code> if name is a built in object type
     */
    static boolean isBuiltInObjType(String typeName) {
        return getObjTypeMap().containsKey(typeName);
    }

    private static synchronized Map<String, ObjTypeInfo> getObjTypeMap() {
        if (sObjTypeMap == null) {
            sObjTypeMap = new HashMap<String, ObjTypeInfo>();
            sObjTypeMap.put(DEVICE_MONITOR_TYPE_NAME, new ObjTypeInfo(IDeviceMonitor.class, true));
            sObjTypeMap.put(DEVICE_MANAGER_TYPE_NAME, new ObjTypeInfo(IDeviceManager.class, false));
            sObjTypeMap.put(DEVICE_REQUIREMENTS_TYPE_NAME, new ObjTypeInfo(IDeviceSelection.class,
                    false));
            sObjTypeMap.put(WTF_HANDLER_TYPE_NAME,
                    new ObjTypeInfo(ITerribleFailureHandler.class, false));
            sObjTypeMap.put(SCHEDULER_TYPE_NAME,
                    new ObjTypeInfo(ICommandScheduler.class, false));

        }
        return sObjTypeMap;
    }

    /**
     * Creates a {@link GlobalConfiguration} with default config objects and stock name/description
     */
    private GlobalConfiguration() {
        this("default", "default global configuration");
    }

    /**
     * Creates a {@link GlobalConfiguration} with default config objects
     */
    GlobalConfiguration(String name, String description) {
        mName = name;
        mDescription = description;
        mConfigMap = new LinkedHashMap<String, List<Object>>();
        mOptionMap = new MultiMap<String, String>();
        setDeviceRequirements(new DeviceSelectionOptions());
        setDeviceManager(new DeviceManager());
        setCommandScheduler(new CommandScheduler());
    }

    /**
     * @return the name of this {@link Configuration}
     */
    public String getName() {
        return mName;
    }

    /**
     * @return a short user readable description this {@link Configuration}
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IDeviceMonitor> getDeviceMonitors() {
        return (List<IDeviceMonitor>) getConfigurationObjectList(DEVICE_MONITOR_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITerribleFailureHandler getWtfHandler() {
        return (ITerribleFailureHandler) getConfigurationObject(WTF_HANDLER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceManager getDeviceManager() {
        return (IDeviceManager)getConfigurationObject(DEVICE_MANAGER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceSelection getDeviceRequirements() {
        return (IDeviceSelection)getConfigurationObject(DEVICE_REQUIREMENTS_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ICommandScheduler getCommandScheduler() {
        return (ICommandScheduler)getConfigurationObject(SCHEDULER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
//    @Override
    public List<?> getConfigurationObjectList(String typeName) {
        return mConfigMap.get(typeName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getConfigurationObject(String typeName) {
        List<?> configObjects = getConfigurationObjectList(typeName);
        if (configObjects == null) {
            return null;
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(typeName);
        if (typeInfo != null && typeInfo.mIsListSupported) {
            throw new IllegalStateException(String.format("Wrong method call. " +
                    "Used getConfigurationObject() for a config object that is stored as a list",
                        typeName));
        }
        if (configObjects.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Attempted to retrieve single object for %s, but %d are present",
                    typeName, configObjects.size()));
        }
        return configObjects.get(0);
    }

    /**
     * Return a copy of all config objects
     */
    private Collection<Object> getAllConfigurationObjects() {
        Collection<Object> objectsCopy = new ArrayList<Object>();
        for (List<Object> objectList : mConfigMap.values()) {
            objectsCopy.addAll(objectList);
        }
        return objectsCopy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValue(String optionName, String optionValue)
            throws ConfigurationException {
        OptionSetter optionSetter = new OptionSetter(getAllConfigurationObjects());
        optionSetter.setOptionValue(optionName, optionValue);

        mOptionMap.put(optionName, optionValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValue(String optionName, String optionKey, String optionValue)
            throws ConfigurationException {
        OptionSetter optionSetter = new OptionSetter(getAllConfigurationObjects());
        optionSetter.setOptionMapValue(optionName, optionKey, optionValue);

        mOptionMap.put(optionName, optionKey + "=" + optionValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getOptionValues(String optionName) {
        return mOptionMap.get(optionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceMonitor(IDeviceMonitor monitor) {
        setConfigurationObjectNoThrow(DEVICE_MONITOR_TYPE_NAME, monitor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWtfHandler(ITerribleFailureHandler wtfHandler) {
        setConfigurationObjectNoThrow(WTF_HANDLER_TYPE_NAME, wtfHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceManager(IDeviceManager manager) {
        setConfigurationObjectNoThrow(DEVICE_MANAGER_TYPE_NAME, manager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceRequirements(IDeviceSelection devRequirements) {
        setConfigurationObjectNoThrow(DEVICE_REQUIREMENTS_TYPE_NAME, devRequirements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandScheduler(ICommandScheduler scheduler) {
        setConfigurationObjectNoThrow(SCHEDULER_TYPE_NAME, scheduler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfigurationObject(String typeName, Object configObject)
            throws ConfigurationException {
        if (configObject == null) {
            throw new IllegalArgumentException("configObject cannot be null");
        }
        mConfigMap.remove(typeName);
        addObject(typeName, configObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfigurationObjectList(String typeName, List<?> configList)
            throws ConfigurationException {
        if (configList == null) {
            throw new IllegalArgumentException("configList cannot be null");
        }
        mConfigMap.remove(typeName);
        for (Object configObject : configList) {
            addObject(typeName, configObject);
        }
    }

    /**
     * Adds a loaded object to this configuration.
     *
     * @param typeName the unique object type name of the configuration object
     * @param configObject the configuration object
     * @throws ConfigurationException if object was not the correct type
     */
    private void addObject(String typeName, Object configObject) throws ConfigurationException {
        List<Object> objList = mConfigMap.get(typeName);
        if (objList == null) {
            objList = new ArrayList<Object>(1);
            mConfigMap.put(typeName, objList);
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(typeName);
        if (typeInfo != null && !typeInfo.mExpectedType.isInstance(configObject)) {
            throw new ConfigurationException(String.format(
                    "The config object %s is not the correct type. Expected %s, received %s",
                    typeName, typeInfo.mExpectedType.getCanonicalName(),
                    configObject.getClass().getCanonicalName()));
        }
        if (typeInfo != null && !typeInfo.mIsListSupported && objList.size() > 0) {
            throw new ConfigurationException(String.format(
                    "Only one config object allowed for %s, but multiple were specified.",
                    typeName));
        }
        objList.add(configObject);
    }

    /**
     * A wrapper around {@link #setConfigurationObject(String, Object)} that will not throw
     * {@link ConfigurationException}.
     * <p/>
     * Intended to be used in cases where its guaranteed that <var>configObject</var> is the
     * correct type.
     *
     * @param typeName
     * @param configObject
     */
    private void setConfigurationObjectNoThrow(String typeName, Object configObject) {
        try {
            setConfigurationObject(typeName, configObject);
        } catch (ConfigurationException e) {
            // should never happen
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> setOptionsFromCommandLineArgs(List<String> listArgs)
            throws ConfigurationException {
        ArgsOptionParser parser = new ArgsOptionParser(getAllConfigurationObjects());
        return parser.parse(listArgs);
    }

    /**
     * Outputs a command line usage help text for this configuration to given printStream.
     *
     * @param out the {@link PrintStream} to use.
     * @throws {@link ConfigurationException}
     */
//    @Override
    public void printCommandUsage(boolean importantOnly, PrintStream out)
            throws ConfigurationException {
        out.println(String.format("'%s' configuration: %s", getName(), getDescription()));
        out.println();
        if (importantOnly) {
            out.println("Printing help for only the important options. " +
                    "To see help for all options, use the --help-all flag");
            out.println();
        }
        for (Map.Entry<String, List<Object>> configObjectsEntry : mConfigMap.entrySet()) {
            for (Object configObject : configObjectsEntry.getValue()) {
                String optionHelp = printOptionsForObject(importantOnly,
                        configObjectsEntry.getKey(), configObject);
                // only print help for object if optionHelp is non zero length
                if (optionHelp.length() > 0) {
                    String classAlias = "";
                    if (configObject.getClass().isAnnotationPresent(OptionClass.class)) {
                        final OptionClass classAnnotation = configObject.getClass().getAnnotation(
                                OptionClass.class);
                        classAlias = String.format("'%s' ", classAnnotation.alias());
                    }
                    out.printf("  %s%s options:", classAlias, configObjectsEntry.getKey());
                    out.println();
                    out.print(optionHelp);
                    out.println();
                }
            }
        }
    }

    /**
     * Prints out the available config options for given configuration object.
     *
     * @param importantOnly print only the important options
     * @param objectTypeName the config object type name. Used to generate more descriptive error
     *            messages
     * @param configObject the config object
     * @return a {@link String} of option help text
     * @throws ConfigurationException
     */
    private String printOptionsForObject(boolean importantOnly, String objectTypeName,
            Object configObject) throws ConfigurationException {
        return ArgsOptionParser.getOptionHelp(importantOnly, configObject);
    }

    /**
     * {@inheritDoc}
     */
//    @Override
    public void validateOptions() throws ConfigurationException {
        new ArgsOptionParser(getAllConfigurationObjects()).validateMandatoryOptions();
    }
}
