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
import com.android.ide.common.blame.SourcePosition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces all placeholders of the form ${name} with a tool invocation provided value
 */
public class PlaceholderHandler {

    // interesting placeholders names that are documented to be automatically provided.
    public static final String INSTRUMENTATION_RUNNER = "instrumentationRunner";
    // When creating a merged manifest for an app module, ${packageName} placeholders are replaced
    // with the applicationId, not the namespace.
    public static final String PACKAGE_NAME = "packageName";
    public static final String APPLICATION_ID = "applicationId";
    public static final String FEATURE_NAME = "featureName";

    // regular expression to recognize placeholders like ${name}, potentially surrounded by a
    // prefix and suffix string. this will split in 3 groups, the prefix, the placeholder name, and
    // the suffix.
    // If this pattern is modified, studio ManifestPlaceholderResolver.PLACEHOLDER_PATTERN must be also change
    static final Pattern PATTERN = Pattern.compile("([^\\$]*)\\$\\{([^\\}]*)\\}(.*)");

    /**
     * Interface to provide a value for a placeholder key.
     * @param <T> the key type
     */
    public interface KeyBasedValueResolver<T> {

        /**
         * Returns a placeholder value for the placeholder key or null if none exists.
         */
        @Nullable
        String getValue(@NotNull T key);
    }

    /**
     * Returns true if the passed string is a placeholder value, false otherwise.
     */
    public static boolean isPlaceHolder(@NotNull String string) {
        return PATTERN.matcher(string).matches();
    }

    /**
     * Visits a document's entire tree and check each attribute for a placeholder existence. If one
     * is found, delegate to the provided {@link KeyBasedValueResolver} to provide a value for the
     * placeholder.
     *
     * <p>If no value is provided, an error will be generated.
     *
     * @param severity the severity of recorded messages regarding placeholders being found without
     *     corresponding values.
     * @param xmlDocument the xml document to visit
     * @param valueProvider the placeholder value provider.
     * @param mergingReportBuilder to report errors and log actions.
     */
    public static void visit(
            @NotNull MergingReport.Record.Severity severity,
            @NotNull XmlDocument xmlDocument,
            @NotNull KeyBasedValueResolver<String> valueProvider,
            @NotNull MergingReport.Builder mergingReportBuilder) {

        visit(severity, xmlDocument.getRootNode(), valueProvider, mergingReportBuilder);
    }

    private static void visit(
            @NotNull MergingReport.Record.Severity severity,
            @NotNull XmlElement xmlElement,
            @NotNull KeyBasedValueResolver<String> valueProvider,
            @NotNull MergingReport.Builder mergingReportBuilder) {

        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {

            StringBuilder resultString = new StringBuilder();
            String inputString = xmlAttribute.getValue();
            Matcher matcher = PATTERN.matcher(inputString);
            if (matcher.matches()) {
                int injected = 0;
                while (matcher.matches()) {
                    String placeholderValue = valueProvider.getValue(matcher.group(2));
                    // whatever precedes the placeholder key is added back to the string.
                    resultString.append(matcher.group(1));
                    if (placeholderValue == null) {
                        mergingReportBuilder.addMessage(
                                xmlAttribute,
                                severity,
                                String.format(
                                        "Attribute %1$s at %2$s requires a placeholder substitution"
                                                + " but no value for <%3$s> is provided.",
                                        xmlAttribute.getId(),
                                        xmlAttribute.printPosition(),
                                        matcher.group(2)));
                        // we add back the placeholder key, since this is not an error for libraries
                        resultString.append(SdkConstants.MANIFEST_PLACEHOLDER_PREFIX);
                        resultString.append(matcher.group(2));
                        resultString.append(SdkConstants.MANIFEST_PLACEHOLDER_SUFFIX);
                    } else {
                        injected++;
                        // substitute the placeholder key with its value.
                        resultString.append(placeholderValue);
                    }
                    // the new input string is the tail of the previous match, as it may contain
                    // more placeholders to substitute.
                    inputString = matcher.group(3);
                    // reset the pattern matching with that new string to test for more placeholders
                    matcher = PATTERN.matcher(inputString);
                }
                // append the last remainder (without placeholders) in the result string.
                resultString.append(inputString);

                xmlAttribute.setValue(resultString.toString());

                if (injected > 0) {
                    // record the action on the element, as it may be the first time we
                    // encounter changes on it.
                    mergingReportBuilder
                            .getActionRecorder()
                            .recordNodeAction(xmlElement, Actions.ActionType.INJECTED);

                    for (int i = 0; i < injected; i++) {
                        // record the attribute set
                        mergingReportBuilder
                                .getActionRecorder()
                                .recordAttributeAction(
                                        xmlAttribute,
                                        SourcePosition.UNKNOWN,
                                        Actions.ActionType.INJECTED,
                                        null /* attributeOperationType */);
                    }
                }

            }
        }
        for (XmlElement childElement : xmlElement.getMergeableElements()) {
            visit(severity, childElement, valueProvider, mergingReportBuilder);
        }
    }
}
