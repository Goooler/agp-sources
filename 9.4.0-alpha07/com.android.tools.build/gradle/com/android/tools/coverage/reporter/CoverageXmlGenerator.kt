/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.coverage.reporter

import java.io.File

/** Main entry point for the host-side Coverage Reporter. Orchestrates decoding, aggregation, and report generation. */
class CoverageXmlGenerator {

  private val decoder = BinaryDecoder()
  private val aggregator = ReportAggregator()
  private val writer = CoverageXmlWriter()

  /**
   * Generates a JaCoCo-compatible XML report from binary artifacts.
   *
   * @param metadataFile Path to coverage_metadata.pb
   * @param hitsFile Path to coverage_hits.pb
   * @param outputFile Destination path for the XML report
   * @param reportName Human-readable name for the report (e.g., "debug")
   * @param testPackageId Optional package ID of the test app to filter out from the report
   * @param exclusions Optional set of class names or package prefixes to exclude
   */
  fun generate(
    metadataFile: File,
    hitsFile: File,
    outputFile: File,
    reportName: String = "debug",
    testPackageId: String? = null,
    exclusions: Set<String> = emptySet(),
  ) {
    val coverageData = decoder.decode(metadataFile, hitsFile)

    val reportModel = aggregator.aggregate(coverageData, reportName, testPackageId, exclusions)

    writer.write(reportModel, outputFile)
  }
}
