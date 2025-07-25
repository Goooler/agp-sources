// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface TestScenarioEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.TestScenarioEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioRequest request = 1 [lazy = true];</code>
   * @return Whether the request field is set.
   */
  boolean hasRequest();
  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioRequest request = 1 [lazy = true];</code>
   * @return The request.
   */
  com.google.wireless.android.sdk.stats.TestScenarioEvent.TestScenarioRequest getRequest();
  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioRequest request = 1 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TestScenarioEvent.TestScenarioRequestOrBuilder getRequestOrBuilder();

  /**
   * <pre>
   * Deprecated this field is no longer necessary
   * </pre>
   *
   * <code>.android_studio.TestScenarioEvent.InconsistentCount inconsistent_count = 2 [deprecated = true, lazy = true];</code>
   * @deprecated android_studio.TestScenarioEvent.inconsistent_count is deprecated.
   *     See studio_stats.proto;l=16091
   * @return Whether the inconsistentCount field is set.
   */
  @java.lang.Deprecated boolean hasInconsistentCount();
  /**
   * <pre>
   * Deprecated this field is no longer necessary
   * </pre>
   *
   * <code>.android_studio.TestScenarioEvent.InconsistentCount inconsistent_count = 2 [deprecated = true, lazy = true];</code>
   * @deprecated android_studio.TestScenarioEvent.inconsistent_count is deprecated.
   *     See studio_stats.proto;l=16091
   * @return The inconsistentCount.
   */
  @java.lang.Deprecated com.google.wireless.android.sdk.stats.TestScenarioEvent.InconsistentCount getInconsistentCount();
  /**
   * <pre>
   * Deprecated this field is no longer necessary
   * </pre>
   *
   * <code>.android_studio.TestScenarioEvent.InconsistentCount inconsistent_count = 2 [deprecated = true, lazy = true];</code>
   */
  @java.lang.Deprecated com.google.wireless.android.sdk.stats.TestScenarioEvent.InconsistentCountOrBuilder getInconsistentCountOrBuilder();

  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioResult test_scenario_result = 3 [lazy = true];</code>
   * @return Whether the testScenarioResult field is set.
   */
  boolean hasTestScenarioResult();
  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioResult test_scenario_result = 3 [lazy = true];</code>
   * @return The testScenarioResult.
   */
  com.google.wireless.android.sdk.stats.TestScenarioEvent.TestScenarioResult getTestScenarioResult();
  /**
   * <code>.android_studio.TestScenarioEvent.TestScenarioResult test_scenario_result = 3 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TestScenarioEvent.TestScenarioResultOrBuilder getTestScenarioResultOrBuilder();

  com.google.wireless.android.sdk.stats.TestScenarioEvent.TransformCase getTransformCase();
}
