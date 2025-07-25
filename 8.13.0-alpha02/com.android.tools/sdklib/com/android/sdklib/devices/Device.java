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

package com.android.sdklib.devices;

import static com.android.sdklib.SystemImageTags.XR_GLASSES_TAG;
import static com.android.sdklib.SystemImageTags.XR_HEADSET_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.dvlib.DeviceSchema;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.sdklib.SystemImageTags;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instances of this class contain the specifications for a device. Use the
 * {@link Builder} class to construct a Device object, or the
 * {@link DeviceParser} if constructing device objects from XML conforming to
 * the {@link DeviceSchema} standards.
 */
public final class Device {

    /** Minimum diagonal size of a tablet, in inches.
     *  A device with a smaller diagonal size is
     *  considered a phone.
     */
    public final static double MINIMUM_TABLET_SIZE = 7.0;

    /** Minimum diagonal size of a TV, in inches.
     *  A device with a smaller diagonal size is
     *  considered a phone or tablet
     */
    public final static double MINIMUM_TV_SIZE = 15.0;

    /** Name of the device */
    @NonNull
    private final String mName;

    /** ID of the device */
    @NonNull
    private final String mId;

    /** Manufacturer of the device */
    @NonNull
    private final String mManufacturer;

    /** True if the device supports Google Play Store */
    private final boolean mHasPlayStore;

    /** A list of software capabilities, one for each API level range */
    @NonNull private final ImmutableList<Software> mSoftware;

    /** A list of phone states (landscape, portrait with keyboard out, etc.) */
    @NonNull private final ImmutableList<State> mState;

    /** Meta information such as icon files and device frames */
    @NonNull
    private final Meta mMeta;

    /** Default state of the device */
    @NonNull
    private final State mDefaultState;

    /** Optional tag-id of the device. */
    @Nullable private final String mTagId;

    /** Optional boot.props of the device. */
    @NonNull private final ImmutableMap<String, String> mBootProps;

    /** If the device should be hidden during AVD creation */
    private final boolean mIsDeprecated;

    /**
     * Returns the name of the {@link Device}. This is intended to be displayed by the user and
     * can vary over time. For a stable internal name of the device, use {@link #getId} instead.
     *
     * @deprecated Use {@link #getId()} or {@link #getDisplayName()} instead based on whether
     *     a stable identifier or a user visible name is needed
     * @return The name of the {@link Device}.
     */
    @NonNull
    @Deprecated
    public String getName() {
        return mName;
    }

    /**
     * Returns the user visible name of the {@link Device}. This is intended to be displayed by the
     * user and can vary over time. For a stable internal name of the device, use {@link #getId}
     * instead.
     *
     * @return The name of the {@link Device}.
     */
    @NonNull
    public String getDisplayName() {
        return mName;
    }

    /**
     * Returns the id of the {@link Device}.
     *
     * @return The id of the {@link Device}.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Returns true if this type of emulated {@link Device} supports Google Play Store.
     */
    public boolean hasPlayStore() {
        return mHasPlayStore;
    }

    /**
     * Returns the manufacturer of the {@link Device}.
     *
     * @return The name of the manufacturer of the {@link Device}.
     */
    @NonNull
    public String getManufacturer() {
        return mManufacturer;
    }

    /**
     * Returns all of the {@link Software} configurations of the {@link Device}.
     *
     * @return A list of all the {@link Software} configurations.
     */
    @NonNull
    public List<Software> getAllSoftware() {
        return mSoftware;
    }

    /**
     * Returns all of the {@link State}s the {@link Device} can be in.
     *
     * @return A list of all the {@link State}s.
     */
    @NonNull
    public List<State> getAllStates() {
        return mState;
    }

    /**
     * Returns the default {@link Hardware} configuration for the device. This
     * is really just a shortcut for getting the {@link Hardware} on the default
     * {@link State}
     *
     * @return The default {@link Hardware} for the device.
     */
    @NonNull
    public Hardware getDefaultHardware() {
        return mDefaultState.getHardware();
    }

    /**
     * Returns the {@link Meta} object for the device, which contains meta
     * information about the device, such as the location of icons.
     *
     * @return The {@link Meta} object for the {@link Device}.
     */
    @NonNull
    public Meta getMeta() {
        return mMeta;
    }

    /**
     * Returns the default {@link State} of the {@link Device}.
     *
     * @return The default {@link State} of the {@link Device}.
     */
    @NonNull
    public State getDefaultState() {
        return mDefaultState;
    }

    /**
     * Returns the software configuration for the given API version.
     *
     * @param apiVersion
     *            The API version requested.
     * @return The Software instance for the requested API version or null if
     *         the API version is unsupported for this device.
     */
    @Nullable
    public Software getSoftware(int apiVersion) {
        for (Software s : mSoftware) {
            if (apiVersion >= s.getMinSdkLevel() && apiVersion <= s.getMaxSdkLevel()) {
                return s;
            }
        }
        return null;
    }

    /**
     * Returns the state of the device with the given name.
     *
     * @param name
     *            The name of the state requested.
     * @return The State object requested or null if there's no state with the
     *         given name.
     */
    @Nullable
    public State getState(String name) {
        for (State s : getAllStates()) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    @SuppressWarnings("SuspiciousNameCombination") // Deliberately swapping orientations
    @Nullable
    public Dimension getScreenSize(@NonNull ScreenOrientation orientation) {
        Screen screen = getDefaultHardware().getScreen();
        if (screen == null) {
            return null;
        }

        // Some foldable devices have different orientations when folded vs unfolded.
        // Screen size usually refers to the unfolded screen size, while
        // orientation refers to the folded orientation.
        Hinge hinge = getDefaultHardware().getHinge();

        if (hinge != null && hinge.getChangeOrientationOnFold()) {
            if (orientation == ScreenOrientation.PORTRAIT) {
                orientation = ScreenOrientation.LANDSCAPE;
            } else if (orientation == ScreenOrientation.LANDSCAPE) {
                orientation = ScreenOrientation.PORTRAIT;
            }
        }

        // compute width and height to take orientation into account.
        int x = screen.getXDimension();
        int y = screen.getYDimension();
        int screenWidth, screenHeight;

        if (x > y) {
            if (orientation == ScreenOrientation.LANDSCAPE) {
                screenWidth = x;
                screenHeight = y;
            }
            else {
                screenWidth = y;
                screenHeight = x;
            }
        }
        else {
            if (orientation == ScreenOrientation.LANDSCAPE) {
                screenWidth = y;
                screenHeight = x;
            }
            else {
                screenWidth = x;
                screenHeight = y;
            }
        }

        return new Dimension(screenWidth, screenHeight);
    }

    /**
     * Returns the optional tag-id of the device.
     *
     * @return the optional tag-id of the device. Can be null.
     */
    @Nullable
    public String getTagId() {
        return mTagId;
    }

    /**
     * Returns the optional boot.props of the device.
     *
     * @return the optional boot.props of the device. Can be null or empty.
     */
    @NonNull
    public Map<String, String> getBootProps() {
        return mBootProps;
    }

    /**
     * A convenience method to get if the screen for this device is round.
     */
    public boolean isScreenRound() {
        return getDefaultHardware().getScreen().getScreenRound() == ScreenRound.ROUND;
    }

    /**
     * A convenience method to get the chin size for this device.
     */
    public int getChinSize() {
        return getDefaultHardware().getScreen().getChin();
    }

    /**
     * Returns true if this device should be hidden during AVD creation.
     * @return true if this device should be hidden during AVD creation.
     */
    public boolean getIsDeprecated() {
        return mIsDeprecated;
    }

    public static class Builder {
        private String mName;
        private String mId;
        private String mManufacturer;
        private boolean mHasPlayStore;
        private final List<Software> mSoftware = new ArrayList<Software>();
        private final List<State> mState = new ArrayList<State>();
        private Meta mMeta;
        private State mDefaultState;
        private String mTagId;
        private final Map<String, String> mBootProps = new TreeMap<String, String>();
        private boolean mDeprecated;

        public Builder() { }

        public Builder(Device d) {
            mTagId = d.getTagId();
            mName = d.getDisplayName();
            mId = d.getId();
            mManufacturer = d.getManufacturer();
            mHasPlayStore = d.hasPlayStore();
            for (Software s : d.getAllSoftware()) {
                mSoftware.add(s.deepCopy());
            }
            for (State s : d.getAllStates()) {
                mState.add(s.deepCopy());
            }
            mMeta = d.getMeta();
            mDeprecated = d.mIsDeprecated;
        }

        public void setName(@NonNull String name) {
            mName = name;
        }

        public void setId(@NonNull String id) {
            mId = id;
        }

        public void setTagId(@Nullable String tagId) {
            mTagId = tagId;
            if (SystemImageTags.WEAR_TAG.getId().equals(mTagId)) {
                // All Wear devices are compatible with Play Store
                mHasPlayStore = true;
            }
        }

        public void addBootProp(@NonNull String propName, @NonNull String propValue) {
            mBootProps.put(propName, propValue);
        }

        public void setManufacturer(@NonNull String manufacturer) {
            mManufacturer = manufacturer;
        }

        public void setPlayStore(boolean hasPlayStore) {
            mHasPlayStore = hasPlayStore;
            if (SystemImageTags.WEAR_TAG.getId().equals(mTagId)) {
                // All Wear devices are compatible with Play Store
                mHasPlayStore = true;
            }
        }

        public void addSoftware(@NonNull Software sw) {
            mSoftware.add(sw);
        }

        public void addAllSoftware(@NonNull Collection<? extends Software> sw) {
            mSoftware.addAll(sw);
        }

        public void addState(State state) {
            mState.add(state);
        }

        public void addAllState(@NonNull Collection<? extends State> states) {
            mState.addAll(states);
        }

        /**
         * Removes the first {@link State} with the given name
         * @param stateName The name of the {@link State} to remove.
         * @return Whether a {@link State} was removed or not.
         */
        public boolean removeState(@NonNull String stateName) {
            for (int i = 0; i < mState.size(); i++) {
                if (stateName != null && stateName.equals(mState.get(i).getName())) {
                    mState.remove(i);
                    return true;
                }
            }
            return false;
        }

        /**
         * Only for use by the {@link DeviceParser}, so that it can modify the states after they've
         * been added.
         */
        List<State> getAllStates() {
            return mState;
        }

        public void setMeta(@NonNull Meta meta) {
            mMeta = meta;
        }

        public void setDeprecated(boolean deprecated) {
            mDeprecated = deprecated;
        }

        public Device build() {
            if (mName == null) {
                throw generateBuildException("Device missing name");
            } else if (mManufacturer == null) {
                throw generateBuildException("Device missing manufacturer");
            } else if (mSoftware.size() <= 0) {
                throw generateBuildException("Device software not configured");
            } else if (mState.size() <= 0) {
                throw generateBuildException("Device states not configured");
            }

            if (mId == null) {
                mId = mName;
            }

            if (mMeta == null) {
                mMeta = new Meta();
            }

            boolean deviceIsRound = false;
            for (State s : mState) {
                if (s.isDefaultState()) {
                    mDefaultState = s;
                }
                Screen screen = s.getHardware().getScreen();
                if (screen != null && ScreenRound.ROUND.equals(screen.getScreenRound())) {
                    deviceIsRound = true;
                }
            }

            if (deviceIsRound) {
                // Ensure that the roundness is one of the boot properties
                mBootProps.put(DeviceParser.ROUND_BOOT_PROP, "true");
            }

            if (mDefaultState == null) {
                throw generateBuildException("Device missing default state");
            }
            return new Device(this);
        }

        private IllegalStateException generateBuildException(String err) {
            String device = "";
            if (mManufacturer != null) {
                device = mManufacturer + ' ';
            }
            if (mName != null) {
                device += mName;
            } else {
                device = "Unknown " + device +"Device";
            }

            return new IllegalStateException("Error building " + device + ": " +err);
        }
    }

    private Device(Builder b) {
        mName = b.mName;
        mId = b.mId;
        mManufacturer = b.mManufacturer;
        mHasPlayStore = b.mHasPlayStore;
        mSoftware = ImmutableList.copyOf(b.mSoftware);
        mState = ImmutableList.copyOf(b.mState);
        mMeta = b.mMeta;
        mDefaultState = b.mDefaultState;
        mTagId = b.mTagId;
        mBootProps = ImmutableMap.copyOf(b.mBootProps);
        mIsDeprecated = b.mDeprecated;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Device)) {
            return false;
        }
        Device d = (Device) o;
        return mName.equals(d.getDisplayName())
                && Objects.equals(mId, d.getId())
                && Objects.equals(mManufacturer, d.getManufacturer())
                && Objects.equals(mSoftware, d.getAllSoftware())
                && Objects.equals(mState, d.getAllStates())
                && Objects.equals(mMeta, d.getMeta())
                && Objects.equals(mDefaultState, d.getDefaultState())
                && mIsDeprecated == d.mIsDeprecated
                && mHasPlayStore == d.mHasPlayStore
                && Objects.equals(mTagId, d.mTagId)
                && Objects.equals(mBootProps, d.mBootProps);
    }


    @Override
    public int hashCode() {
        return Objects.hash(
                mName,
                mId,
                mManufacturer,
                mSoftware,
                mState,
                mMeta,
                mDefaultState,
                mIsDeprecated,
                mHasPlayStore,
                mTagId,
                mBootProps);
    }


    /** toString value suitable for debugging only. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device [mName=");
        sb.append(mName);
        sb.append(", mId=");
        sb.append(mId);
        sb.append(", mManufacturer=");
        sb.append(mManufacturer);
        sb.append(", mSoftware=");
        sb.append(mSoftware);
        sb.append(", mState=");
        sb.append(mState);
        sb.append(", mMeta=");
        sb.append(mMeta);
        sb.append(", mDefaultState=");
        sb.append(mDefaultState);
        sb.append(", mTagId=");
        sb.append(mTagId);
        sb.append(", mBootProps=");
        sb.append(mBootProps);
        sb.append("]");
        return sb.toString();
    }

    private static Pattern PATTERN = Pattern.compile(
    "(\\d+\\.?\\d*)(?:in|\") (.+?)( \\(.*Nexus.*\\))?"); //$NON-NLS-1$

    /**
     * Returns a "sortable" name for the device -- if a device list is sorted
     * using this sort-aware display name, it will be displayed in an order that
     * is user friendly with devices using names first sorted alphabetically
     * followed by all devices that use a numeric screen size sorted by actual
     * size.
     * <p>
     * Note that although the name results in a proper sort, it is not a name
     * that you actually want to display to the user.
     * <p>
     * Extracted from DeviceMenuListener. Modified to remove the leading space
     * insertion as it doesn't render neatly in the avd manager. Instead added
     * the option to add leading zeroes to make the string names sort properly.
     *
     * Replace "'in'" with '"' (e.g. 2.7" QVGA instead of 2.7in QVGA).
     * Use the same precision for all devices (all but one specify decimals).
     */
    private String getSortableName() {
        String sortableName = mName;
        Matcher matcher = PATTERN.matcher(sortableName);
        if (matcher.matches()) {
            String size = matcher.group(1);
            String n = matcher.group(2);
            int dot = size.indexOf('.');
            if (dot == -1) {
                size = size + ".0";
                dot = size.length() - 2;
            }
            if (dot < 3) {
                // Pad to have at least 3 digits before the dot, for sorting
                // purposes.
                // We can revisit this once we get devices that are more than
                // 999 inches wide.
                size = "000".substring(dot) + size;
            }
            sortableName = size + "\" " + n;
        }

        return sortableName;
    }

    /**
     * Returns a comparator suitable to sort a device list using a sort-aware display name.
     * The list is displayed in an order that is user friendly with devices using names
     * first sorted alphabetically followed by all devices that use a numeric screen size
     * sorted by actual size.
     */
    public static Comparator<Device> getDisplayComparator() {
        return new Comparator<Device>() {
            @Override
            public int compare(Device d1, Device d2) {
                String s1 = d1.getSortableName();
                String s2 = d2.getSortableName();
                if (s1.length() > 1 && s2.length() > 1) {
                    int i1 = Character.isDigit(s1.charAt(0)) ? 1 : 0;
                    int i2 = Character.isDigit(s2.charAt(0)) ? 1 : 0;
                    if (i1 != i2) {
                        return i1 - i2;
                    }
                }
                return s1.compareTo(s2);
            }};
    }

    public static boolean isRollable(@NonNull String deviceId) {
        // TODO: b/304585541 - Declare this in XML
        return deviceId.equals("7.4in Rollable");
    }

    public static boolean isPhone(@NonNull Device device) {
        return device.getTagId() == null && !hasTabletScreen(device);
    }

    public static boolean isTablet(@NonNull Device device) {
        return device.getTagId() == null && hasTabletScreen(device);
    }

    // TODO: http://b/326289372 -  Declare this in XML
    private static boolean hasTabletScreen(@NonNull Device device) {
        Screen screen = device.getDefaultHardware().getScreen();
        return screen.getDiagonalLength() >= MINIMUM_TABLET_SIZE && !screen.isFoldable();
    }

    /** Whether the given device is a wear device */
    public static boolean isWear(@Nullable Device device) {
        return device != null && "android-wear".equals(device.getTagId());
    }

    /** Whether the given device is an Android Things device */
    public static boolean isThings(@Nullable Device device) {
        return device != null && "android-things".equals(device.getTagId());
    }

    /** Whether the given device is a TV device */
    public static boolean isTv(@Nullable Device device) {
        return device != null
                && ("android-tv".equals(device.getTagId())
                        || "google-tv".equals(device.getTagId()));
    }

    /** Whether the given device is an Automotive device */
    public static boolean isAutomotive(@Nullable Device device) {
        return device != null
                && ("android-automotive".equals(device.getTagId())
                        || "android-automotive-playstore".equals(device.getTagId())
                        || "android-automotive-distantdisplay".equals(device.getTagId()));
    }

    /** Whether the given automotive device has distant display */
    public static boolean isAutomotiveDistantDisplay(@Nullable Device device) {
        return device != null && "android-automotive-distantdisplay".equals(device.getTagId());
    }

    /** Whether the given device is a PC device */
    public static boolean isDesktop(@Nullable Device device) {
        return device != null && "android-desktop".equals(device.getTagId());
    }

    /** Whether the given device is an XR device */
    public static boolean isXr(@Nullable Device device) {
        String tagId = device != null ? device.getTagId() : null;
        return tagId != null && tagId.startsWith("android-xr");
    }

    /** Whether the given device is an XR Headset device */
    public static boolean isXrHeadset(@Nullable Device device) {
        return XR_HEADSET_TAG.getId().equals(device != null ? device.getTagId() : null);
    }

    /** Whether the given device is an XR Glasses device */
    public static boolean isXrGlasses(@Nullable Device device) {
        return XR_GLASSES_TAG.getId().equals(device != null ? device.getTagId() : null);
    }

    /** Whether the given device appears to be a mobile device (e.g. not wear, tv, auto, etc) */
    public static boolean isMobile(@Nullable Device device) {
        return !isTv(device)
                && !isWear(device)
                && !isThings(device)
                && !isAutomotive(device)
                && !isDesktop(device)
                && !isXr(device);
    }
}
