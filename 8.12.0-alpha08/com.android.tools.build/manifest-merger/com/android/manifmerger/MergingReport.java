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

import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

/**
 * Contains the result of 2 files merging.
 *
 * TODO: more work necessary, this is pretty raw as it stands.
 */
@Immutable
public class MergingReport {

    public enum MergedManifestKind {
        /**
         * Merged manifest file with unresolved placeholders encoded to be AAPT friendly.
         */
        AAPT_SAFE,

        /** Blame file for merged manifest file. */
        BLAME,

        /** Merged manifest file as packaged in the APK or AAR */
        MERGED,
    }

    @NotNull
    private final Map<MergedManifestKind, String> mergedDocuments;
    @NotNull
    private final Map<MergedManifestKind, XmlDocument> mergedXmlDocuments;
    @NotNull
    private final Result result;
    // list of logging events, ordered by their recording time.
    @NotNull
    private final ImmutableList<Record> records;
    @NotNull
    private final ImmutableList<String> intermediaryStages;
    @NotNull
    private final Actions actions;

    /**
     * In some cases AAPT manifest is the same as merged one, so we can save time by reusing the
     * merged manifest
     */
    @NotNull private final boolean isAaptSafeManifestUnchanged;

    private MergingReport(
            @NotNull Map<MergedManifestKind, String> mergedDocuments,
            @NotNull Map<MergedManifestKind, XmlDocument> mergedXmlDocuments,
            @NotNull Result result,
            @NotNull ImmutableList<Record> records,
            @NotNull ImmutableList<String> intermediaryStages,
            @NotNull Actions actions,
            @NotNull boolean isAaptSafeManifestUnchanged) {
        this.mergedDocuments = mergedDocuments;
        this.mergedXmlDocuments = mergedXmlDocuments;
        this.result = result;
        this.records = records;
        this.intermediaryStages = intermediaryStages;
        this.actions = actions;
        this.isAaptSafeManifestUnchanged = isAaptSafeManifestUnchanged;
    }

    /**
     * dumps all logging records to a logger.
     */
    public void log(@NotNull ILogger logger) {
        for (Record record : records) {
            switch(record.mSeverity) {
                case WARNING:
                    logger.warning(record.toString());
                    break;
                case ERROR:
                    logger.error(null /* throwable */, record.toString());
                    break;
                case INFO:
                    logger.verbose(record.toString());
                    break;
                default:
                    logger.error(null /* throwable */, "Unhandled record type " + record.mSeverity);
            }
        }
        actions.log(logger);

        if (!result.isSuccess()) {
            logger.warning(
                    "\nSee https://developer.android.com/r/studio-ui/build/manifest-merger for more information"
                            + " about the manifest merger.\n");
        }
    }

    @Nullable
    public String getMergedDocument(@NotNull MergedManifestKind state) {
        return mergedDocuments.get(state);
    }

    public boolean isAaptSafeManifestUnchanged() {
        return isAaptSafeManifestUnchanged;
    }

    @Nullable
    public XmlDocument getMergedXmlDocument(@NotNull MergedManifestKind state) {
        return mergedXmlDocuments.get(state);
    }

    /**
     * Returns all the merging intermediary stages if
     * {@link com.android.manifmerger.ManifestMerger2.Invoker.Feature#KEEP_INTERMEDIARY_STAGES}
     * is set.
     */
    @NotNull
    public ImmutableList<String> getIntermediaryStages() {
        return intermediaryStages;
    }

    /**
     * Overall result of the merging process.
     */
    public enum Result {
        SUCCESS,

        WARNING,

        ERROR;

        public boolean isSuccess() {
            return this == SUCCESS || this == WARNING;
        }

        public boolean isWarning() {
            return this == WARNING;
        }

        public boolean isError() {
            return this == ERROR;
        }
    }

    @NotNull
    public Result getResult() {
        return result;
    }

    @NotNull
    public ImmutableList<Record> getLoggingRecords() {
        return records;
    }

    @NotNull
    public Actions getActions() {
        return actions;
    }

    @NotNull
    public String getReportString() {
        switch (result) {
            case SUCCESS:
                return "Manifest merger executed successfully";
            case WARNING:
                return records.size() > 1
                        ? "Manifest merger exited with warnings, see logs"
                        : "Manifest merger warning : " + records.get(0).mLog;
            case ERROR:
                return records.size() > 1
                        ? "Manifest merger failed with multiple errors, see logs"
                        : "Manifest merger failed : " + records.get(0).mLog;
            default:
                return "Manifest merger returned an invalid result " + result;
        }
    }

    /**
     * Log record. This is used to give users some information about what is happening and what
     * might have gone wrong.
     */
    public static final class Record {

        public enum Severity {WARNING, ERROR, INFO }

        @NotNull
        private final Severity mSeverity;
        @NotNull
        private final String mLog;
        @NotNull
        private final SourceFilePosition mSourceLocation;

        private Record(
                @NotNull SourceFilePosition sourceLocation,
                @NotNull Severity severity,
                @NotNull String mLog) {
            this.mSourceLocation = sourceLocation;
            this.mSeverity = severity;
            this.mLog = mLog;
        }

        @NotNull
        public Severity getSeverity() {
            return mSeverity;
        }

        @NotNull
        public String getMessage() {
            return mLog;
        }

        @NotNull
        public SourceFilePosition getSourceLocation() {
            return mSourceLocation;
        }

        @NotNull
        @Override
        public String toString() {
            return mSourceLocation.toString() // needs short string.
                    + " "
                    + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mSeverity.toString())
                    + ":\n\t"
                    + mLog;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Record)) {
                return false;
            }
            Record that = (Record) obj;

            return Objects.equals(that.mSeverity, mSeverity)
                    && Objects.equals(that.mSourceLocation, mSourceLocation)
                    && Objects.equals(that.mLog, mLog);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSeverity, mSourceLocation, mLog);
        }
    }

    /**
     * This builder is used to accumulate logging, action recording and intermediary results as well
     * as final result of the merging activity.
     *
     * <p>Once the merging is finished, the {@link #build()} is called to return an immutable
     * version of itself with all the logging, action recordings and xml files obtainable.
     */
    public static class Builder {

        private Map<MergedManifestKind, String> mergedDocuments =
                new EnumMap<>(MergedManifestKind.class);
        private Map<MergedManifestKind, XmlDocument> mergedXmlDocuments =
                new EnumMap<>(MergedManifestKind.class);

        @NotNull private ImmutableSet.Builder<Record> mRecordBuilder = new ImmutableSet.Builder<>();

        @NotNull
        private ImmutableList.Builder<String> mIntermediaryStages = new ImmutableList.Builder<>();

        private boolean mHasWarnings = false;
        private boolean mHasErrors = false;
        @NotNull
        private ActionRecorder mActionRecorder = new ActionRecorder();
        @NotNull private final ILogger mLogger;
        private boolean isAaptSafeManifestUnchanged = false;

        Builder(@NotNull ILogger logger) {
            mLogger = logger;
        }

        Builder setMergedDocument(@NotNull MergedManifestKind mergedManifestKind, @NotNull String mergedDocument) {
            this.mergedDocuments.put(mergedManifestKind, mergedDocument);
            return this;
        }

        Builder setAaptSafeManifestUnchanged(boolean aaptSafeManifestUnchanged) {
            this.isAaptSafeManifestUnchanged = aaptSafeManifestUnchanged;
            return this;
        }

        Builder setMergedXmlDocument(@NotNull XmlDocument mergedDocument) {
            this.mergedXmlDocuments.put(MergedManifestKind.MERGED, mergedDocument);
            return this;
        }

        @NotNull
        @VisibleForTesting
        Builder addMessage(@NotNull SourceFile sourceFile,
                int line,
                int column,
                @NotNull Record.Severity severity,
                @NotNull String message) {
            // The line and column used are 1-based, but SourcePosition uses zero-based.
            return addMessage(
                    new SourceFilePosition(sourceFile, new SourcePosition(line - 1, column -1, -1)),
                    severity,
                    message);
        }

        @NotNull
        Builder addMessage(@NotNull SourceFile sourceFile,
                @NotNull Record.Severity severity,
                @NotNull String message) {
            return addMessage(
                    new SourceFilePosition(sourceFile, SourcePosition.UNKNOWN),
                    severity,
                    message);
        }

        void addMessage(
                @NotNull XmlElement element,
                @NotNull MergingReport.Record.Severity severity,
                @NotNull String message) {
            addMessage(element.getSourceFilePosition(), severity, message);
        }

        @NotNull
        Builder addMessage(
                @NotNull XmlAttribute attribute,
                @NotNull MergingReport.Record.Severity severity,
                @NotNull String message) {
            return addMessage(attribute, attribute.getPosition(), severity, message);
        }

        @NotNull
        Builder addMessage(
                @NotNull XmlAttribute attribute,
                @NotNull SourcePosition position,
                @NotNull MergingReport.Record.Severity severity,
                @NotNull String message) {
            return addMessage(
                    new SourceFilePosition(
                            attribute.getOwnerElement().getDocument().getSourceFile(), position),
                    severity,
                    message);
        }

        @NotNull
        Builder addMessage(@NotNull SourceFilePosition sourceFilePosition,
                    @NotNull Record.Severity severity,
                    @NotNull String message) {
            switch (severity) {
                case ERROR:
                    mHasErrors = true;
                    break;
                case WARNING:
                    mHasWarnings = true;
                    break;
            }
            mRecordBuilder.add(new Record(sourceFilePosition,  severity, message));
            return this;
        }

        @NotNull
        Builder addMergingStage(@NotNull String xml) {
            mIntermediaryStages.add(xml);
            return this;
        }

        /**
         * Returns true if some fatal errors were reported.
         */
        boolean hasErrors() {
            return mHasErrors;
        }

        @NotNull
        ActionRecorder getActionRecorder() {
            return mActionRecorder;
        }

        @NotNull
        MergingReport build() {
            Result result = mHasErrors
                    ? Result.ERROR
                    : mHasWarnings
                            ? Result.WARNING
                            : Result.SUCCESS;

            return new MergingReport(
                    mergedDocuments,
                    mergedXmlDocuments,
                    result,
                    ImmutableList.copyOf(mRecordBuilder.build()),
                    mIntermediaryStages.build(),
                    mActionRecorder.build(),
                    isAaptSafeManifestUnchanged);
        }

        @NotNull
        public ILogger getLogger() {
            return mLogger;
        }

        public String blame(XmlDocument document)
                throws ParserConfigurationException, SAXException, IOException {
            return mActionRecorder.build().blame(document);
        }
    }
}
