/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.test.report

/** Defines the complete data model for the final JSON report. */

/**
 * The root of the test report data model.
 *
 * @property projectName The name of the project.
 * @property timestamp The timestamp of when the report was generated.
 * @property numberOfModules The total count of modules.
 * @property numberOfPackages The total count of packages.
 * @property numberOfClasses The total count of classes.
 * @property variants A list of all variant names included in this report.
 * @property testSuites A list of all unique test suite names.
 * @property modules A list of modules in the project.
 */
data class RootReport(
  val projectName: String,
  val timestamp: String,
  val numberOfModules: Int,
  val numberOfPackages: Int,
  val numberOfClasses: Int,
  val variants: List<String>,
  val testSuites: List<String>,
  val modules: List<Module>,
)

/**
 * Represents a Gradle module in the test report.
 *
 * @property name The path of the module (e.g., ":app").
 * @property testSuiteSummaries Summaries of the test suites within this module.
 * @property packages A list of Java/Kotlin packages containing tests.
 */
data class Module(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val packages: List<Package>)

/**
 * Represents a Java/Kotlin package containing test classes.
 *
 * @property name The package name (e.g., "com.example.mytapp").
 * @property testSuiteSummaries Summaries of the test suites within this package.
 * @property classes A list of test classes within this package.
 */
data class Package(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val classes: List<ClassType>)

/**
 * Represents a test class.
 *
 * @property name The simple name of the class (e.g., "ExampleUnitTest").
 * @property testSuiteSummaries Summaries of the test suites within this class.
 * @property testCases A list of test methods (test cases) in this class.
 */
data class ClassType(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val testCases: List<TestCase>)

/**
 * Represents the execution result of a test case for a specific variant.
 *
 * @property status The result status (e.g., "passed", "failed", "skipped").
 * @property stackTraceId The ID of the stack trace if the test failed, referencing [TestCase.commonStackTraces].
 * @property diffPercent The percentage difference in the screenshot test.
 * @property previewName The name of the preview used in the screenshot test.
 * @property methodName The method name of the screenshot test.
 * @property refImagePath The path to the reference image.
 * @property newImagePath The path to the newly generated image.
 * @property diffImagePath The path to the image showing the differences.
 */
data class VariantTestResult(
  val status: String,
  val stackTraceId: String? = null,
  val diffPercent: String? = null,
  val previewName: String? = null,
  val methodName: String? = null,
  val refImagePath: String? = null,
  val newImagePath: String? = null,
  val diffImagePath: String? = null,
)

/**
 * Groups test results for a specific test suite within a test case.
 *
 * @property testSuiteName The name of the test suite.
 * @property variantResults A map where keys are variant names and values are the [VariantTestResult] for that variant.
 */
data class TestSuiteTestResult(val testSuiteName: String, val variantResults: Map<String, VariantTestResult>)

/**
 * Represents a test method with results across multiple suites and variants.
 *
 * @property name The name of the test case.
 * @property testSuiteSummaries Summaries of the test suites for this specific test case.
 * @property testSuiteResults A list of results grouped by test suite.
 * @property commonStackTraces A list of unique stack traces encountered for this test case.
 */
data class TestCase(
  val name: String,
  val testSuiteSummaries: List<TestSuiteSummary>,
  val testSuiteResults: List<TestSuiteTestResult>,
  val commonStackTraces: List<StackTraceGroup>,
)

/**
 * Represents a unique stack trace and its occurrences.
 *
 * @property id A unique identifier for this stack trace.
 * @property stackTrace The actual stack trace text.
 * @property occurrences A map where keys are test suite names and values are lists of variant names where this stack trace occurred.
 */
data class StackTraceGroup(val id: String, val stackTrace: String, val occurrences: Map<String, List<String>>)

/**
 * Represents a summary of a test suite.
 *
 * @property name The name of the test suite.
 * @property variantSummaries A list of summaries per variant.
 */
data class TestSuiteSummary(val name: String, val variantSummaries: List<VariantSummary>)

/**
 * Represents a summary of test results for a specific variant.
 *
 * @property name The name of the variant.
 * @property passed The number of passed tests.
 * @property failed The number of failed tests.
 * @property skipped The number of skipped tests.
 * @property total The total number of tests.
 * @property rate The percentage of passed tests (passed / (passed + failed)).
 */
data class VariantSummary(val name: String, val passed: Int, val failed: Int, val skipped: Int, val total: Int, val rate: Double)
