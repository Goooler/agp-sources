// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface ApkAnalyzerStatsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.ApkAnalyzerStats)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * size of the APK
   * </pre>
   *
   * <code>optional uint64 uncompressed_size = 1;</code>
   * @return Whether the uncompressedSize field is set.
   */
  boolean hasUncompressedSize();
  /**
   * <pre>
   * size of the APK
   * </pre>
   *
   * <code>optional uint64 uncompressed_size = 1;</code>
   * @return The uncompressedSize.
   */
  long getUncompressedSize();

  /**
   * <pre>
   * size of the APK after compression
   * </pre>
   *
   * <code>optional uint64 compressed_size = 2;</code>
   * @return Whether the compressedSize field is set.
   */
  boolean hasCompressedSize();
  /**
   * <pre>
   * size of the APK after compression
   * </pre>
   *
   * <code>optional uint64 compressed_size = 2;</code>
   * @return The compressedSize.
   */
  long getCompressedSize();

  /**
   * <code>optional .android_studio.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType align16_type = 3;</code>
   * @return Whether the align16Type field is set.
   */
  boolean hasAlign16Type();
  /**
   * <code>optional .android_studio.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType align16_type = 3;</code>
   * @return The align16Type.
   */
  com.google.wireless.android.sdk.stats.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType getAlign16Type();
}
