/*
 * Copyright 2011 the original author or authors.
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

package com.android.build.gradle.internal.test.report;

import static org.gradle.api.tasks.testing.TestResult.ResultType;

import com.android.builder.core.BuilderConstants;
import com.android.utils.FileUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Custom CompositeTestResults based on Gradle's CompositeTestResults */
public abstract class CompositeTestResults extends TestResultModel {
    // Reserve space for the ".html" extension within the 255-byte POSIX NAME_MAX limit
    // (which also keeps UTF-16 code units <= 255 on Windows).
    static final int MAX_FILENAME_UTF8_BYTES = 255 - ".html".length();

    private static final Pattern WINDOWS_RESERVED_NAME_PATTERN =
            Pattern.compile(
                    "^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$", Pattern.CASE_INSENSITIVE);

    private final CompositeTestResults parent;
    private String uniqueFilename;
    private int tests;
    private final Set<TestResult> failures = new TreeSet<>();
    private int skips = 0;
    private long duration;

    private final Map<String, DeviceTestResults> devices = new TreeMap<>();
    private final Map<String, VariantTestResults> variants = new TreeMap<>();

    private final Map<String, StringBuilder> standardOutput = new TreeMap<>();
    private final Map<String, StringBuilder> standardError = new TreeMap<>();

    protected CompositeTestResults(CompositeTestResults parent) {
        this.parent = parent;
    }

    void setFilename(String filename) {
        this.uniqueFilename = filename;
    }

    public String getFilename(ReportType reportType) {
        if (uniqueFilename != null) {
            return uniqueFilename;
        }
        return sanitizeFilename(getName());
    }

    static String sanitizeFilename(String name) {
        if (name == null) {
            return "_";
        }
        // name can come from untrusted device-reported test class/package names.
        // Preserve existing whitespace/colon replacement, then sanitize path separators,
        // OS-unsafe characters, control/format characters, and URL-reserved '#' / '%'
        // so generated report files cannot escape the report directory or break href links.
        String sanitized =
                FileUtils.sanitizeFileName(name.replaceAll("[\\s:]", "-"))
                        .replaceAll("[\\p{Cc}\\p{Cf}#%]", "_");
        return finalizeSanitizedFilename(sanitized, MAX_FILENAME_UTF8_BYTES);
    }

    static String finalizeSanitizedFilename(String sanitized, int maxUtf8Bytes) {
        String truncated = truncateUtf8(sanitized, maxUtf8Bytes);
        if (truncated.isEmpty()) {
            return "_";
        }
        if (truncated.chars().allMatch(c -> c == '.')) {
            return truncated.replace('.', '_');
        }
        if (WINDOWS_RESERVED_NAME_PATTERN.matcher(truncated).matches()) {
            if (maxUtf8Bytes <= 1) {
                return "_";
            }
            return "_" + truncateUtf8(truncated, maxUtf8Bytes - 1);
        }
        return truncated;
    }

    private static String truncateUtf8(String value, int maxUtf8Bytes) {
        if (maxUtf8Bytes <= 0) {
            return "";
        }
        int byteCount = 0;
        int i = 0;
        while (i < value.length()) {
            int codePoint = value.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            int cpBytes;
            if (codePoint <= 0x7F) {
                cpBytes = 1;
            } else if (codePoint <= 0x7FF) {
                cpBytes = 2;
            } else if (codePoint <= 0xFFFF) {
                cpBytes = 3;
            } else {
                cpBytes = 4;
            }
            if (byteCount + cpBytes > maxUtf8Bytes) {
                break;
            }
            byteCount += cpBytes;
            i += charCount;
        }
        return value.substring(0, i);
    }

    public abstract String getName();

    public int getTestCount() {
        return tests;
    }

    public int getFailureCount() {
        return failures.size();
    }

    public int getSkipCount() {
        return skips;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public String getFormattedDuration() {
        return getTestCount() == 0 ? "-" : super.getFormattedDuration();
    }

    public Set<TestResult> getFailures() {
        return failures;
    }

    Map<String, DeviceTestResults> getResultsPerDevices() {
        return devices;
    }

    Map<String, VariantTestResults> getResultsPerVariants() {
        return variants;
    }

    @Override
    public ResultType getResultType() {
        return failures.isEmpty() ? ResultType.SUCCESS : ResultType.FAILURE;
    }

    public String getFormattedSuccessRate() {
        Number successRate = getSuccessRate();
        if (successRate == null) {
            return "-";
        }
        return successRate + "%";
    }

    public Number getSuccessRate() {
        if (getTestCount() == 0 || getTestCount() == getSkipCount()) {
            return null;
        }

        BigDecimal tests = BigDecimal.valueOf(getTestCount() - getSkipCount());
        BigDecimal successful =
                BigDecimal.valueOf(getTestCount() - getFailureCount() - getSkipCount());

        return successful.divide(tests, 2,
                BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).intValue();
    }

    protected void failed(TestResult failedTest,
                          String deviceName, String projectName, String flavorName) {
        failures.add(failedTest);
        if (parent != null) {
            parent.failed(failedTest, deviceName, projectName, flavorName);
        }

        DeviceTestResults deviceResults = devices.get(deviceName);
        if (deviceResults != null) {
            deviceResults.failed(failedTest, deviceName, projectName, flavorName);
        }

        String key = getVariantKey(projectName, flavorName);
        VariantTestResults variantResults = variants.get(key);
        if (variantResults != null) {
            variantResults.failed(failedTest, deviceName, projectName, flavorName);
        }
    }

    protected void skipped(String deviceName, String projectName, String flavorName) {
        skips++;
        if (parent != null) {
            parent.skipped(deviceName, projectName, flavorName);
        }

        DeviceTestResults deviceResults = devices.get(deviceName);
        if (deviceResults != null) {
            deviceResults.skipped(deviceName, projectName, flavorName);
        }

        String key = getVariantKey(projectName, flavorName);
        VariantTestResults variantResults = variants.get(key);
        if (variantResults != null) {
            variantResults.skipped(deviceName, projectName, flavorName);
        }
    }

    protected TestResult addTest(TestResult test) {
        tests++;
        duration += test.getDuration();
        return test;
    }

    protected void addDevice(String deviceName, TestResult testResult) {
        if (deviceName == null) {
            deviceName = "";
        }
        DeviceTestResults deviceResults = devices.get(deviceName);
        if (deviceResults == null) {
            deviceResults = new DeviceTestResults(deviceName, null);
            devices.put(deviceName, deviceResults);
        }

        deviceResults.addTest(testResult);
    }

    protected void addVariant(String projectName, String flavorName, TestResult testResult) {
        if (projectName == null) {
            projectName = "";
        }
        if (flavorName == null) {
            flavorName = "";
        }
        String key = getVariantKey(projectName, flavorName);
        VariantTestResults variantResults = variants.get(key);
        if (variantResults == null) {
            variantResults = new VariantTestResults(key, null);
            variants.put(key, variantResults);
        }

        variantResults.addTest(testResult);
    }

    private static String getVariantKey(String projectName, String flavorName) {
        if (BuilderConstants.MAIN.equalsIgnoreCase(flavorName)) {
            return projectName;
        }

        return projectName + ":" + flavorName;
    }

    public void addStandardOutput(String deviceName, String textContent) {
        standardOutput.compute(
                deviceName,
                (key, currentValue) -> {
                    if (currentValue == null) {
                        return new StringBuilder(textContent);
                    } else {
                        return currentValue.append(textContent);
                    }
                });
    }

    public Map<String, StringBuilder> getStandardOutputPerDevices() {
        return standardOutput;
    }

    public void addStandardError(String deviceName, String textContent) {
        standardError.compute(
                deviceName,
                (key, currentValue) -> {
                    if (currentValue == null) {
                        return new StringBuilder(textContent);
                    } else {
                        return currentValue.append(textContent);
                    }
                });
    }

    public Map<String, StringBuilder> getStandardErrorPerDevices() {
        return standardError;
    }
}
