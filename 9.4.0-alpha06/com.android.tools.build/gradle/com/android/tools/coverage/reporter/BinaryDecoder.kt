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

import com.android.tools.coverage.proto.CoverageMetadataProto.CoverageHits
import com.android.tools.coverage.proto.CoverageMetadataProto.CoverageMetadata
import java.io.File
import java.util.BitSet

/**
 * Represents the combined coverage data (metadata + hits) in a format optimized for report generation.
 *
 * @property metadata The structural metadata collected by the agent.
 * @property hits A BitSet representing which basic block IDs were executed.
 */
data class CoverageData(val metadata: CoverageMetadata, val hits: BitSet)

/** Utility for decoding binary coverage artifacts into an in-memory representation. */
class BinaryDecoder {

  /**
   * Reads and merges metadata and hits from the provided files.
   *
   * @param metadataFile The .pb file containing the structural mapping.
   * @param hitsFile The .pb file containing the execution bitmask.
   * @return A [CoverageData] object containing the merged results.
   */
  fun decode(metadataFile: File, hitsFile: File): CoverageData {
    if (!metadataFile.exists()) {
      throw IllegalArgumentException("Metadata file not found: ${metadataFile.absolutePath}")
    }
    if (!hitsFile.exists()) {
      throw IllegalArgumentException("Hits file not found: ${hitsFile.absolutePath}")
    }

    val metadata = metadataFile.inputStream().use { CoverageMetadata.parseFrom(it) }
    val hitsProto = hitsFile.inputStream().use { CoverageHits.parseFrom(it) }

    // BitSet.valueOf treats the byte array as a little-endian representation
    // of bits, which matches how we serialize it in hits_extractor.cc.
    val bitSet = BitSet.valueOf(hitsProto.hitMask.toByteArray())

    return CoverageData(metadata, bitSet)
  }
}
