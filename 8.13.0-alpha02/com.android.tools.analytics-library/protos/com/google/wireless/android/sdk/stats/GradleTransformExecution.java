// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * Protobuf type {@code android_studio.GradleTransformExecution}
 */
public final class GradleTransformExecution extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.GradleTransformExecution)
    GradleTransformExecutionOrBuilder {
private static final long serialVersionUID = 0L;
  // Use GradleTransformExecution.newBuilder() to construct.
  private GradleTransformExecution(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private GradleTransformExecution() {
    transformClassName_ = "";
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new GradleTransformExecution();
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GradleTransformExecution_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GradleTransformExecution_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.GradleTransformExecution.class, com.google.wireless.android.sdk.stats.GradleTransformExecution.Builder.class);
  }

  private int bitField0_;
  public static final int TYPE_FIELD_NUMBER = 1;
  private int type_ = 0;
  /**
   * <pre>
   * The transform implementing class.
   * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
   * This is an enum in android git:
   * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
   * Historical, was used before adding transform_class_name field
   * </pre>
   *
   * <code>optional int32 type = 1 [deprecated = true];</code>
   * @deprecated android_studio.GradleTransformExecution.type is deprecated.
   *     See studio_stats.proto;l=5612
   * @return Whether the type field is set.
   */
  @java.lang.Override
  @java.lang.Deprecated public boolean hasType() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * The transform implementing class.
   * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
   * This is an enum in android git:
   * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
   * Historical, was used before adding transform_class_name field
   * </pre>
   *
   * <code>optional int32 type = 1 [deprecated = true];</code>
   * @deprecated android_studio.GradleTransformExecution.type is deprecated.
   *     See studio_stats.proto;l=5612
   * @return The type.
   */
  @java.lang.Override
  @java.lang.Deprecated public int getType() {
    return type_;
  }

  public static final int IS_INCREMENTAL_FIELD_NUMBER = 2;
  private boolean isIncremental_ = false;
  /**
   * <pre>
   * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
   * or was (in the case of TASK_TRANSFORM) incremental.
   * </pre>
   *
   * <code>optional bool is_incremental = 2;</code>
   * @return Whether the isIncremental field is set.
   */
  @java.lang.Override
  public boolean hasIsIncremental() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
   * or was (in the case of TASK_TRANSFORM) incremental.
   * </pre>
   *
   * <code>optional bool is_incremental = 2;</code>
   * @return The isIncremental.
   */
  @java.lang.Override
  public boolean getIsIncremental() {
    return isIncremental_;
  }

  public static final int TRANSFORM_CLASS_NAME_FIELD_NUMBER = 3;
  @SuppressWarnings("serial")
  private volatile java.lang.Object transformClassName_ = "";
  /**
   * <pre>
   * Name of the class executing the transform.
   * Example: ShrinkResourcesTransform
   * </pre>
   *
   * <code>optional string transform_class_name = 3;</code>
   * @return Whether the transformClassName field is set.
   */
  @java.lang.Override
  public boolean hasTransformClassName() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <pre>
   * Name of the class executing the transform.
   * Example: ShrinkResourcesTransform
   * </pre>
   *
   * <code>optional string transform_class_name = 3;</code>
   * @return The transformClassName.
   */
  @java.lang.Override
  public java.lang.String getTransformClassName() {
    java.lang.Object ref = transformClassName_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      if (bs.isValidUtf8()) {
        transformClassName_ = s;
      }
      return s;
    }
  }
  /**
   * <pre>
   * Name of the class executing the transform.
   * Example: ShrinkResourcesTransform
   * </pre>
   *
   * <code>optional string transform_class_name = 3;</code>
   * @return The bytes for transformClassName.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getTransformClassNameBytes() {
    java.lang.Object ref = transformClassName_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      transformClassName_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (((bitField0_ & 0x00000001) != 0)) {
      output.writeInt32(1, type_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeBool(2, isIncremental_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 3, transformClassName_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (((bitField0_ & 0x00000001) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt32Size(1, type_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(2, isIncremental_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, transformClassName_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.google.wireless.android.sdk.stats.GradleTransformExecution)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.GradleTransformExecution other = (com.google.wireless.android.sdk.stats.GradleTransformExecution) obj;

    if (hasType() != other.hasType()) return false;
    if (hasType()) {
      if (getType()
          != other.getType()) return false;
    }
    if (hasIsIncremental() != other.hasIsIncremental()) return false;
    if (hasIsIncremental()) {
      if (getIsIncremental()
          != other.getIsIncremental()) return false;
    }
    if (hasTransformClassName() != other.hasTransformClassName()) return false;
    if (hasTransformClassName()) {
      if (!getTransformClassName()
          .equals(other.getTransformClassName())) return false;
    }
    if (!getUnknownFields().equals(other.getUnknownFields())) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (hasType()) {
      hash = (37 * hash) + TYPE_FIELD_NUMBER;
      hash = (53 * hash) + getType();
    }
    if (hasIsIncremental()) {
      hash = (37 * hash) + IS_INCREMENTAL_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getIsIncremental());
    }
    if (hasTransformClassName()) {
      hash = (37 * hash) + TRANSFORM_CLASS_NAME_FIELD_NUMBER;
      hash = (53 * hash) + getTransformClassName().hashCode();
    }
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GradleTransformExecution parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.GradleTransformExecution prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code android_studio.GradleTransformExecution}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.GradleTransformExecution)
      com.google.wireless.android.sdk.stats.GradleTransformExecutionOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GradleTransformExecution_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GradleTransformExecution_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.GradleTransformExecution.class, com.google.wireless.android.sdk.stats.GradleTransformExecution.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.GradleTransformExecution.newBuilder()
    private Builder() {

    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);

    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      bitField0_ = 0;
      type_ = 0;
      isIncremental_ = false;
      transformClassName_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GradleTransformExecution_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GradleTransformExecution getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.GradleTransformExecution.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GradleTransformExecution build() {
      com.google.wireless.android.sdk.stats.GradleTransformExecution result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GradleTransformExecution buildPartial() {
      com.google.wireless.android.sdk.stats.GradleTransformExecution result = new com.google.wireless.android.sdk.stats.GradleTransformExecution(this);
      if (bitField0_ != 0) { buildPartial0(result); }
      onBuilt();
      return result;
    }

    private void buildPartial0(com.google.wireless.android.sdk.stats.GradleTransformExecution result) {
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.type_ = type_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.isIncremental_ = isIncremental_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.transformClassName_ = transformClassName_;
        to_bitField0_ |= 0x00000004;
      }
      result.bitField0_ |= to_bitField0_;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.wireless.android.sdk.stats.GradleTransformExecution) {
        return mergeFrom((com.google.wireless.android.sdk.stats.GradleTransformExecution)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.GradleTransformExecution other) {
      if (other == com.google.wireless.android.sdk.stats.GradleTransformExecution.getDefaultInstance()) return this;
      if (other.hasType()) {
        setType(other.getType());
      }
      if (other.hasIsIncremental()) {
        setIsIncremental(other.getIsIncremental());
      }
      if (other.hasTransformClassName()) {
        transformClassName_ = other.transformClassName_;
        bitField0_ |= 0x00000004;
        onChanged();
      }
      this.mergeUnknownFields(other.getUnknownFields());
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 8: {
              type_ = input.readInt32();
              bitField0_ |= 0x00000001;
              break;
            } // case 8
            case 16: {
              isIncremental_ = input.readBool();
              bitField0_ |= 0x00000002;
              break;
            } // case 16
            case 26: {
              transformClassName_ = input.readBytes();
              bitField0_ |= 0x00000004;
              break;
            } // case 26
            default: {
              if (!super.parseUnknownField(input, extensionRegistry, tag)) {
                done = true; // was an endgroup tag
              }
              break;
            } // default:
          } // switch (tag)
        } // while (!done)
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.unwrapIOException();
      } finally {
        onChanged();
      } // finally
      return this;
    }
    private int bitField0_;

    private int type_ ;
    /**
     * <pre>
     * The transform implementing class.
     * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
     * This is an enum in android git:
     * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
     * Historical, was used before adding transform_class_name field
     * </pre>
     *
     * <code>optional int32 type = 1 [deprecated = true];</code>
     * @deprecated android_studio.GradleTransformExecution.type is deprecated.
     *     See studio_stats.proto;l=5612
     * @return Whether the type field is set.
     */
    @java.lang.Override
    @java.lang.Deprecated public boolean hasType() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * The transform implementing class.
     * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
     * This is an enum in android git:
     * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
     * Historical, was used before adding transform_class_name field
     * </pre>
     *
     * <code>optional int32 type = 1 [deprecated = true];</code>
     * @deprecated android_studio.GradleTransformExecution.type is deprecated.
     *     See studio_stats.proto;l=5612
     * @return The type.
     */
    @java.lang.Override
    @java.lang.Deprecated public int getType() {
      return type_;
    }
    /**
     * <pre>
     * The transform implementing class.
     * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
     * This is an enum in android git:
     * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
     * Historical, was used before adding transform_class_name field
     * </pre>
     *
     * <code>optional int32 type = 1 [deprecated = true];</code>
     * @deprecated android_studio.GradleTransformExecution.type is deprecated.
     *     See studio_stats.proto;l=5612
     * @param value The type to set.
     * @return This builder for chaining.
     */
    @java.lang.Deprecated public Builder setType(int value) {

      type_ = value;
      bitField0_ |= 0x00000001;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The transform implementing class.
     * Custom transforms are recorded as UNKNOWN_TRANSFORM_TYPE.
     * This is an enum in android git:
     * tools/analytics-library/protos/src/main/proto/analytics_enums.proto
     * Historical, was used before adding transform_class_name field
     * </pre>
     *
     * <code>optional int32 type = 1 [deprecated = true];</code>
     * @deprecated android_studio.GradleTransformExecution.type is deprecated.
     *     See studio_stats.proto;l=5612
     * @return This builder for chaining.
     */
    @java.lang.Deprecated public Builder clearType() {
      bitField0_ = (bitField0_ & ~0x00000001);
      type_ = 0;
      onChanged();
      return this;
    }

    private boolean isIncremental_ ;
    /**
     * <pre>
     * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
     * or was (in the case of TASK_TRANSFORM) incremental.
     * </pre>
     *
     * <code>optional bool is_incremental = 2;</code>
     * @return Whether the isIncremental field is set.
     */
    @java.lang.Override
    public boolean hasIsIncremental() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
     * or was (in the case of TASK_TRANSFORM) incremental.
     * </pre>
     *
     * <code>optional bool is_incremental = 2;</code>
     * @return The isIncremental.
     */
    @java.lang.Override
    public boolean getIsIncremental() {
      return isIncremental_;
    }
    /**
     * <pre>
     * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
     * or was (in the case of TASK_TRANSFORM) incremental.
     * </pre>
     *
     * <code>optional bool is_incremental = 2;</code>
     * @param value The isIncremental to set.
     * @return This builder for chaining.
     */
    public Builder setIsIncremental(boolean value) {

      isIncremental_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Whether the transform could be (in the case of TASK_TRANSFORM_PREPARATION)
     * or was (in the case of TASK_TRANSFORM) incremental.
     * </pre>
     *
     * <code>optional bool is_incremental = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearIsIncremental() {
      bitField0_ = (bitField0_ & ~0x00000002);
      isIncremental_ = false;
      onChanged();
      return this;
    }

    private java.lang.Object transformClassName_ = "";
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @return Whether the transformClassName field is set.
     */
    public boolean hasTransformClassName() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @return The transformClassName.
     */
    public java.lang.String getTransformClassName() {
      java.lang.Object ref = transformClassName_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          transformClassName_ = s;
        }
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @return The bytes for transformClassName.
     */
    public com.google.protobuf.ByteString
        getTransformClassNameBytes() {
      java.lang.Object ref = transformClassName_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        transformClassName_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @param value The transformClassName to set.
     * @return This builder for chaining.
     */
    public Builder setTransformClassName(
        java.lang.String value) {
      if (value == null) { throw new NullPointerException(); }
      transformClassName_ = value;
      bitField0_ |= 0x00000004;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearTransformClassName() {
      transformClassName_ = getDefaultInstance().getTransformClassName();
      bitField0_ = (bitField0_ & ~0x00000004);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the class executing the transform.
     * Example: ShrinkResourcesTransform
     * </pre>
     *
     * <code>optional string transform_class_name = 3;</code>
     * @param value The bytes for transformClassName to set.
     * @return This builder for chaining.
     */
    public Builder setTransformClassNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) { throw new NullPointerException(); }
      transformClassName_ = value;
      bitField0_ |= 0x00000004;
      onChanged();
      return this;
    }
    @java.lang.Override
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:android_studio.GradleTransformExecution)
  }

  // @@protoc_insertion_point(class_scope:android_studio.GradleTransformExecution)
  private static final com.google.wireless.android.sdk.stats.GradleTransformExecution DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.GradleTransformExecution();
  }

  public static com.google.wireless.android.sdk.stats.GradleTransformExecution getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<GradleTransformExecution>
      PARSER = new com.google.protobuf.AbstractParser<GradleTransformExecution>() {
    @java.lang.Override
    public GradleTransformExecution parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try {
        builder.mergeFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(builder.buildPartial());
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(builder.buildPartial());
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(e)
            .setUnfinishedMessage(builder.buildPartial());
      }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<GradleTransformExecution> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<GradleTransformExecution> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.GradleTransformExecution getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

