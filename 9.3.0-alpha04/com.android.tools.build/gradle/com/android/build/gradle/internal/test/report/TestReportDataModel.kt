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
 * @property variants A list of all variant names included in this report (e.g., "debug", "release"). These are used as keys in the
 *   [TestCase.results] map.
 * @property modules A list of modules in the project (e.g., ":app", ":lib").
 * @property summary The aggregated summary of all tests in this report.
 */
data class RootReport(
  val projectName: String,
  val timestamp: String,
  val numberOfModules: Int,
  val numberOfPackages: Int,
  val numberOfClasses: Int,
  val variants: List<String>,
  val modules: List<Module>,
  val summary: TestSummary,
)

/**
 * Represents a Gradle module in the test report.
 *
 * @property name The path of the module (e.g., ":app").
 * @property testSuiteSummaries Summaries of the test suites within this module.
 * @property packages A list of Java/Kotlin packages containing tests.
 * @property summary The aggregated summary of all tests in this module.
 */
data class Module(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val packages: List<Package>, val summary: TestSummary)

/**
 * Represents a Java/Kotlin package containing test classes.
 *
 * @property name The package name (e.g., "com.example.mytapp").
 * @property testSuiteSummaries Summaries of the test suites within this package.
 * @property classes A list of test classes within this package.
 * @property summary The aggregated summary of all tests in this package.
 */
data class Package(val name: String, val testSuiteSummaries: List<TestSuiteSummary>, val classes: List<ClassType>, val summary: TestSummary)

/**
 * Represents a test class.
 *
 * @property name The simple name of the class (e.g., "ExampleUnitTest").
 * @property testSuiteSummaries Summaries of the test suites within this class.
 * @property testCases A list of test methods (test cases) in this class.
 * @property summary The aggregated summary of all tests in this class.
 */
data class ClassType(
  val name: String,
  val testSuiteSummaries: List<TestSuiteSummary>,
  val testCases: List<TestCase>,
  val summary: TestSummary,
)

/**
 * Represents a single test function execution result.
 *
 * @property status The result status (e.g., "passed", "failed", "skipped").
 * @property stackTrace The stack trace if the test failed, or null otherwise.
 */
data class TestResults(val status: String, val stackTrace: String? = null)

/**
 * Represents a test method with results across multiple variants.
 *
 * @property name The name of the test case (e.g., "testAddition").
 * @property results A map where keys are variant names (matching [RootReport.variants]) and values are the [TestResults] for that variant.
 *   This allows aggregating results for the same test across different build variants.
 */
data class TestCase(
  val name: String,
  // Store TestResult objects instead of simple Strings
  val results: Map<String, TestResults> = emptyMap(),
)

/**
 * Represents a summary of a test suite.
 *
 * @property name The name of the test suite (e.g., "UnitTest", "AndroidTest").
 * @property summary The aggregated summary of all tests in this test suite.
 */
data class TestSuiteSummary(val name: String, val summary: TestSummary)

/**
 * Represents a summary of test results.
 *
 * @property total The total number of tests.
 * @property passed The number of passed tests.
 * @property failed The number of failed tests.
 * @property skipped The number of skipped tests.
 * @property passRate The percentage of passed tests (passed / (passed + failed)).
 * @property variantSummaries A map of summaries per variant.
 */
data class TestSummary(
  val total: Int,
  val passed: Int,
  val failed: Int,
  val skipped: Int,
  val passRate: Double,
  val variantSummaries: Map<String, VariantSummary>,
)

/**
 * Represents a summary of test results for a specific variant.
 *
 * @property passed The number of passed tests.
 * @property failed The number of failed tests.
 * @property skipped The number of skipped tests.
 * @property total The total number of tests.
 * @property rate The percentage of passed tests.
 */
data class VariantSummary(val passed: Int, val failed: Int, val skipped: Int, val total: Int, val rate: Double)
