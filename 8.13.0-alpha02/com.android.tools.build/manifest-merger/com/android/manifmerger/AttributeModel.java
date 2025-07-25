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

package com.android.manifmerger;

import com.android.SdkConstants;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes an attribute characteristics like if it supports smart package name replacement, has
 * a default value and a validator for its values.
 */
class AttributeModel {
    private static final Pattern PACKAGE_NAME_PATTERN =
            Pattern.compile("([A-Za-z][A-Za-z\\d_]*\\.)*[A-Za-z][A-Za-z\\d_]*");

    @NotNull private final XmlNode.NodeName mXmlNodeName;
    private final boolean mIsPackageDependent;
    @Nullable private final String mDefaultValue;
    @Nullable private final Validator mOnReadValidator;
    @Nullable private final Validator mOnWriteValidator;
    @NotNull private final MergingPolicy mMergingPolicy;

    /**
     * Define a new attribute with specific characteristics.
     *
     * @param xmlNodeName namespaced name of the attribute, as created by e.g.
     *     XmlNode.fromNSName(SdkConstants.ANDROID_URI, "android", attributeName));
     * @param isPackageDependent true if the attribute support smart substitution of package name.
     * @param defaultValue an optional default value.
     * @param onReadValidator an optional validator to validate values against.
     */
    private AttributeModel(
            @NotNull XmlNode.NodeName xmlNodeName,
            boolean isPackageDependent,
            @Nullable String defaultValue,
            @Nullable Validator onReadValidator,
            @Nullable Validator onWriteValidator,
            @NotNull MergingPolicy mergingPolicy) {
        mXmlNodeName = xmlNodeName;
        mIsPackageDependent = isPackageDependent;
        mDefaultValue = defaultValue;
        mOnReadValidator = onReadValidator;
        mOnWriteValidator = onWriteValidator;
        mMergingPolicy = mergingPolicy;
    }

    @NotNull
    XmlNode.NodeName getName() {
        return mXmlNodeName;
    }

    /**
     * Return true if the attribute support smart substitution of partially fully qualified
     * class names with package settings as provided by the manifest node's package attribute
     * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>}
     *
     * @return true if this attribute supports smart substitution or false if not.
     */
    boolean isPackageDependent() {
        return mIsPackageDependent;
    }

    /**
     * Returns the attribute's default value or null if none.
     */
    @Nullable
    String getDefaultValue() {
        return mDefaultValue;
    }

    /**
     * Returns the attribute's {@link com.android.manifmerger.AttributeModel.Validator} to
     * validate its value when read from xml files or null if no validation is necessary.
     */
    @Nullable
    public Validator getOnReadValidator() {
        return mOnReadValidator;
    }

    /**
     * Returns the attribute's {@link com.android.manifmerger.AttributeModel.Validator} to
     * validate its value when the merged file is about to be persisted.
     */
    @Nullable
    public Validator getOnWriteValidator() {
        return mOnWriteValidator;
    }

    /**
     * Returns the {@link com.android.manifmerger.AttributeModel.MergingPolicy} for this
     * attribute.
     */
    @NotNull
    public MergingPolicy getMergingPolicy() {
        return mMergingPolicy;
    }

    /**
     * Creates a new {@link Builder} to describe an attribute.
     * @param attributeName the to be described attribute name
     */
    @NotNull
    static Builder newModel(String attributeName) {
        return new Builder(XmlNode.fromNSName(SdkConstants.ANDROID_URI, "android", attributeName));
    }


    static class Builder {
        private final XmlNode.NodeName mXmlNodeName;
        private boolean mIsPackageDependent = false;
        private String mDefaultValue;
        private Validator mOnReadValidator;
        private Validator mOnWriteValidator;
        @NotNull
        private MergingPolicy mMergingPolicy = STRICT_MERGING_POLICY;

        Builder(XmlNode.NodeName xmlNodeName) {
            mXmlNodeName = xmlNodeName;
        }

        /**
         * Sets the attribute support for smart substitution of partially fully qualified
         * class names with package settings as provided by the manifest node's package attribute
         * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>}
         */
        @NotNull
        Builder setIsPackageDependent() {
            mIsPackageDependent = true;
            return this;
        }

        /**
         * Sets the attribute default value.
         */
        @NotNull
        Builder setDefaultValue(String value) {
            mDefaultValue =  value;
            return this;
        }

        /**
         * Sets a {@link com.android.manifmerger.AttributeModel.Validator} to validate the
         * attribute's values coming from xml files.
         */
        @NotNull
        Builder setOnReadValidator(Validator validator) {
            mOnReadValidator = validator;
            return this;
        }

        /**
         * Sets a {@link com.android.manifmerger.AttributeModel.Validator} to validate values
         * before they are written to the final merged document.
         */
        @NotNull
        Builder setOnWriteValidator(Validator validator) {
            mOnWriteValidator = validator;
            return this;
        }

        @NotNull
        Builder setMergingPolicy(@NotNull MergingPolicy mergingPolicy) {
            mMergingPolicy = mergingPolicy;
            return this;
        }

        /**
         * Build an immutable {@link com.android.manifmerger.AttributeModel}
         */
        @NotNull
        AttributeModel build() {
            return new AttributeModel(
                    mXmlNodeName,
                    mIsPackageDependent,
                    mDefaultValue,
                    mOnReadValidator,
                    mOnWriteValidator,
                    mMergingPolicy);
        }
    }

    /**
     * Defines a merging policy between two attribute values. Example of merging policies can be
     * strict when it is illegal to try to merge or override a value by another. Another example
     * is a OR merging policy on boolean attribute values.
     */
    interface MergingPolicy {

        /**
         * Returns true if it should be attempted to merge this attribute value with
         * the attribute default value when merging with a node that does not contain
         * the attribute declaration.
         */
        boolean shouldMergeDefaultValues();

        /**
         * Returns true if this attribute can be merged from the provided {@link XmlDocument}, false
         * otherwise
         */
        default boolean canMergeWithLowerPriority(@NotNull XmlDocument document) {
            return true;
        }

        /**
         * Returns true if this attribute should only be included when present on both manifests
         * being merged, false otherwise
         */
        default boolean removeIfNotPresentOnBothManifests() {
            return false;
        }

        /**
         * Merges the two attributes values and returns the merged value. If the values cannot be
         * merged, return null.
         */
        @Nullable
        String merge(@NotNull String higherPriority, @NotNull String lowerPriority);
    }

    /**
     * Standard attribute value merging policy, generates an error unless both values are equal.
     */
    @NotNull
    static final MergingPolicy STRICT_MERGING_POLICY = new MergingPolicy() {

        @Override
        public boolean shouldMergeDefaultValues() {
            return false;
        }

        @Nullable
        @Override
        public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
            // it's ok if the values are equal, otherwise it's not.
            return higherPriority.equals(lowerPriority)
                    ? higherPriority
                    : null;
        }
    };

    /** Similar to STRICT_MERGING_POLICY, but only allows merging from MAIN or OVERLAY manifests */
    @NotNull
    static final MergingPolicy STRICT_MAIN_OR_OVERLAY_MERGING_POLICY =
            new MergingPolicy() {

                @Override
                public boolean shouldMergeDefaultValues() {
                    return false;
                }

                @Override
                public boolean canMergeWithLowerPriority(@NotNull XmlDocument document) {
                    return EnumSet.of(XmlDocument.Type.MAIN, XmlDocument.Type.OVERLAY)
                            .contains(document.getFileType());
                }

                @Nullable
                @Override
                public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
                    return STRICT_MERGING_POLICY.merge(higherPriority, lowerPriority);
                }
            };

    /**
     * Boolean OR merging policy.
     */
    static final MergingPolicy OR_MERGING_POLICY = new MergingPolicy() {
        @Override
        public boolean shouldMergeDefaultValues() {
            return true;
        }

        @Nullable
        @Override
        public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
            return Boolean.toString(BooleanValidator.isTrue(higherPriority) ||
                    BooleanValidator.isTrue(lowerPriority));
        }
    };

    /**
     * Merging policy that will return the higher priority value regardless of the lower priority
     * value
     */
    static final MergingPolicy NO_MERGING_POLICY = new MergingPolicy() {

        @Override
        public boolean shouldMergeDefaultValues() {
            return true;
        }

        @Nullable
        @Override
        public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
            return higherPriority;
        }
    };

    static final MergingPolicy AND_MERGING_POLICY =
            new MergingPolicy() {
                @Override
                public boolean shouldMergeDefaultValues() {
                    return true;
                }

                @Nullable
                @Override
                public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
                    return Boolean.toString(
                            BooleanValidator.isTrue(higherPriority)
                                    && BooleanValidator.isTrue(lowerPriority));
                }
            };

    static final MergingPolicy STRING_VALUE_MERGING_POLICY =
            new MergingPolicy() {
                @Override
                public boolean shouldMergeDefaultValues() {
                    return true;
                }

                @Nullable
                @Override
                public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
                    return String.format("%s|%s", higherPriority, lowerPriority);
                }
            };

    static final MergingPolicy PURPOSE_MIN_SDK_VERSION_MERGING_POLICY =
            new MergingPolicy() {
                @Override
                public boolean shouldMergeDefaultValues() {
                    return true;
                }

                @Override
                public boolean removeIfNotPresentOnBothManifests() {
                    return true;
                }

                @Nullable
                @Override
                public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
                    if (Integer.parseInt(higherPriority) > Integer.parseInt(lowerPriority)) {
                        return lowerPriority;
                    } else {
                        return higherPriority;
                    }
                }
            };

    static final MergingPolicy PURPOSE_MAX_SDK_VERSION_MERGING_POLICY =
            new MergingPolicy() {
                @Override
                public boolean shouldMergeDefaultValues() {
                    return true;
                }

                @Override
                public boolean removeIfNotPresentOnBothManifests() {
                    return true;
                }

                @Nullable
                @Override
                public String merge(@NotNull String higherPriority, @NotNull String lowerPriority) {
                    if (Integer.parseInt(higherPriority) > Integer.parseInt(lowerPriority)) {
                        return higherPriority;
                    } else {
                        return lowerPriority;
                    }
                }
            };

    /**
     * Decode a decimal or hexadecimal {@link String} into an {@link Integer}.
     * String starting with 0 will be considered decimal, not octal.
     */
    private static int decodeDecOrHexString(@NotNull String s) {
        long decodedValue = s.startsWith("0x") || s.startsWith("0X")
                ? Long.decode(s)
                : Long.parseLong(s);
        if (decodedValue < 0xFFFFFFFFL) {
            return (int) decodedValue;
        } else {
            throw new IllegalArgumentException("Value " + s + " too big for 32 bits.");
        }
    }

    /**
     * Validates an attribute value.
     *
     * The validator can be called when xml documents are read to ensure the xml file contains
     * valid statements.
     *
     * This is a poor-mans replacement for not having a proper XML Schema do perform such
     * validations.
     */
    interface Validator {

        /**
         * Validates a value, issuing a warning or error in case of failure through the passed
         * merging report.
         * @param mergingReport to report validation warnings or error
         * @param attribute the attribute to validate.
         * @param value the proposed or existing attribute value.
         * @return true if the value is legal for this attribute.
         */
        boolean validates(@NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute,
                @NotNull String value);
    }

    /**
     * Validates a boolean attribute type.
     */
    static class BooleanValidator implements Validator {

        // TODO: check with @xav where to find the acceptable values by runtime.

        private static final Pattern BOOL_RESOURCE_REF_PATTERN =
                Pattern.compile("[@?]" + "(" + PACKAGE_NAME_PATTERN + ":)" + "?bool/\\w+");
        private static final Pattern TRUE_PATTERN =
                Pattern.compile("true|True|TRUE|" + BOOL_RESOURCE_REF_PATTERN);
        private static final Pattern FALSE_PATTERN =
                Pattern.compile("false|False|FALSE|" + BOOL_RESOURCE_REF_PATTERN);

        private static boolean isTrue(@NotNull String value) {
            return TRUE_PATTERN.matcher(value).matches();
        }

        @Override
        public boolean validates(@NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute,
                @NotNull String value) {
            boolean matches = TRUE_PATTERN.matcher(value).matches() ||
                    FALSE_PATTERN.matcher(value).matches();
            if (!matches) {
                mergingReport.addMessage(
                        attribute,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Attribute %1$s at %2$s has an illegal value=(%3$s), "
                                        + "expected 'true' or 'false'",
                                attribute.getId(), attribute.printPosition(), value));
            }
            return matches;
        }
    }

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} for verifying that each value in a
     * string of delimiter-separated values is an acceptable value, and that there's at least one
     * value in the string of delimiter-separated values.
     */
    static class SeparatedValuesValidator implements Validator {

        @NotNull private final ImmutableList<String> multiValuesList;
        @NotNull private final String delimiter;

        SeparatedValuesValidator(@NotNull String delimiter, @NotNull String... multiValues) {
            this.multiValuesList = ImmutableList.copyOf(multiValues);
            this.delimiter = delimiter;
        }

        @Override
        public boolean validates(
                @NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute,
                @NotNull String value) {
            boolean result = true;
            List<String> delimitedValues = Arrays.asList(value.split(Pattern.quote(delimiter)));
            if (delimitedValues.isEmpty()) {
                result = false;
            }
            for (String delimitedValue : delimitedValues) {
                if (!multiValuesList.contains(delimitedValue)) {
                    result = false;
                    break;
                }
            }
            if (!result) {
                mergingReport.addMessage(
                        attribute,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Invalid value for attribute %1$s at %2$s, value=(%3$s), "
                                        + "acceptable delimiter-separated values are (%4$s)",
                                attribute.getId(),
                                attribute.printPosition(),
                                value,
                                Joiner.on(delimiter).join(multiValuesList)));
            }
            return result;
        }
    }

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} for verifying that a proposed
     * value is a numerical integer value.
     */
    static class IntegerValueValidator implements Validator {
        private static final Pattern INTEGER_RESOURCE_REF_PATTERN =
                Pattern.compile("[@?]" + "(" + PACKAGE_NAME_PATTERN + ":)" + "?integer/\\w+");

        @Override
        public boolean validates(@NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute, @NotNull String value) {
            try {
                return Integer.parseInt(value) > 0;
            } catch (NumberFormatException e) {
                if (INTEGER_RESOURCE_REF_PATTERN.matcher(value).matches()) {
                    return true;
                }
                mergingReport.addMessage(
                        attribute,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Attribute %1$s at %2$s must be an integer, found %3$s",
                                attribute.getId(), attribute.printPosition(), value));
                return false;
            }
        }
    }

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} to validate that a string is
     * a valid 32 bits hexadecimal representation.
     */
    static class Hexadecimal32Bits implements Validator {
        protected static final Pattern PATTERN = Pattern.compile("0[xX]([0-9a-fA-F]+)");

        @Override
        public boolean validates(@NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute, @NotNull String value) {
            Matcher matcher = PATTERN.matcher(value);
            boolean valid = matcher.matches() && matcher.group(1).length() <= 8;
            if (!valid) {
                mergingReport.addMessage(
                        attribute,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Attribute %1$s at %2$s is not a valid hexadecimal 32 bit value,"
                                        + " found %3$s",
                                attribute.getId(), attribute.printPosition(), value));
            }
            return valid;
        }
    }

    /**
     * A {@link com.android.manifmerger.AttributeModel.Validator} to validate that a string is
     * a valid 32 positive hexadecimal representation with a minimum value requirement.
     */
    static class Hexadecimal32BitsWithMinimumValue extends Hexadecimal32Bits {

        private final int mMinimumValue;

        Hexadecimal32BitsWithMinimumValue(int minimumValue) {
            mMinimumValue = minimumValue;
        }

        @Override
        public boolean validates(@NotNull MergingReport.Builder mergingReport,
                @NotNull XmlAttribute attribute, @NotNull String value) {
            boolean valid = super.validates(mergingReport, attribute, value);
            if (valid) {
                try {
                    Long decodedValue = Long.decode(value);
                    valid = decodedValue >= mMinimumValue && decodedValue < 0xFFFFFFFFL;
                } catch(NumberFormatException e) {
                    valid = false;
                }
                if (!valid) {
                    mergingReport.addMessage(
                            attribute,
                            MergingReport.Record.Severity.ERROR,
                            String.format(
                                    "Attribute %1$s at %2$s is not a valid hexadecimal value,"
                                        + " minimum is 0x%3$08X, maximum is 0x%4$08X, found %5$s",
                                    attribute.getId(),
                                    attribute.printPosition(),
                                    mMinimumValue,
                                    Integer.MAX_VALUE,
                                    value));
                }
                return valid;
            }
            return false;
        }
    }
}
