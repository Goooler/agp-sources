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
import com.android.ide.common.blame.MessageJsonSerializer;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.utils.ILogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.LineReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Contains all actions taken during a merging invocation.
 */
@Immutable
public class Actions {

    // TODO: i18n
    @VisibleForTesting
    static final String HEADER = "-- Merging decision tree log ---\n";

    // defines all the records for the merging tool activity, indexed by element name+key.
    // iterator should be ordered by the key insertion order.
    private final Map<XmlNode.NodeKey, DecisionTreeRecord> mRecords;

    public Actions(Map<XmlNode.NodeKey, DecisionTreeRecord> records) {
        mRecords = records;
    }

    /**
     * Returns a {@link com.google.common.collect.ImmutableSet} of all the element's keys that have
     * at least one {@link NodeRecord}.
     */
    @NotNull
    public Set<XmlNode.NodeKey> getNodeKeys() {
        return mRecords.keySet();
    }

    /**
     * Returns an {@link ImmutableList} of {@link NodeRecord} for the element identified with the
     * passed key.
     */
    @NotNull
    public ImmutableList<NodeRecord> getNodeRecords(@NotNull XmlNode.NodeKey key) {
        return mRecords.containsKey(key) ? mRecords.get(key).getNodeRecords() : ImmutableList.of();
    }

    /**
     * Returns a {@link ImmutableList} of all attributes names that have at least one record for
     * the element identified with the passed key.
     */
    @NotNull
    public ImmutableList<XmlNode.NodeName> getRecordedAttributeNames(XmlNode.NodeKey nodeKey) {
        DecisionTreeRecord decisionTreeRecord = mRecords.get(nodeKey);
        if (decisionTreeRecord == null) {
            return ImmutableList.of();
        }
        return decisionTreeRecord.getAttributesRecords().keySet().asList();
    }

    /**
     * Returns the {@link com.google.common.collect.ImmutableList} of {@link AttributeRecord} for
     * the attribute identified by attributeName of the element identified by elementKey.
     */
    @NotNull
    public ImmutableList<AttributeRecord> getAttributeRecords(XmlNode.NodeKey elementKey,
            XmlNode.NodeName attributeName) {

        DecisionTreeRecord decisionTreeRecord = mRecords.get(elementKey);
        if (decisionTreeRecord == null) {
            return ImmutableList.of();
        }
        return decisionTreeRecord.getAttributeRecords(attributeName);
    }

    /**
     * Initial dump of the merging tool actions, need to be refined and spec'ed out properly.
     * @param logger logger to log to at INFO level.
     */
    void log(@NotNull ILogger logger) {
        logger.verbose(getLogs());
    }

    /**
     * Dump merging tool actions to a text file.
     *
     * @param fileWriter the file to write all actions into.
     * @throws IOException when logging failed.
     */
    void log(@NotNull FileWriter fileWriter) throws IOException {
        fileWriter.append(getLogs());
    }

    private String getLogs() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(HEADER);
        for (Map.Entry<XmlNode.NodeKey, Actions.DecisionTreeRecord> record : mRecords.entrySet()) {
            stringBuilder.append(record.getKey()).append("\n");
            for (Actions.NodeRecord nodeRecord : record.getValue().getNodeRecords()) {
                nodeRecord.print(stringBuilder);
                stringBuilder.append('\n');
            }
            for (Map.Entry<XmlNode.NodeName, List<Actions.AttributeRecord>> attributeRecords :
                    record.getValue().mAttributeRecords.entrySet()) {
                stringBuilder.append('\t').append(attributeRecords.getKey()).append('\n');
                for (Actions.AttributeRecord attributeRecord : attributeRecords.getValue()) {
                    stringBuilder.append("\t\t");
                    attributeRecord.print(stringBuilder);
                    stringBuilder.append('\n');
                }
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Defines all possible actions taken from the merging tool for an xml element or attribute.
     */
    public enum ActionType {
        /**
         * The element was added into the resulting merged manifest.
         */
        ADDED,
        /**
         * The element was injected from the merger invocation parameters.
         */
        INJECTED,
        /**
         * The element was merged with another element into the resulting merged manifest.
         */
        MERGED,
        /**
         * The element was rejected.
         */
        REJECTED,
        /**
         * The implied element was added was added when importing a library that expected the
         * element to be present by default while targeted SDK requires its declaration.
         */
        IMPLIED,
        /**
         * The element was converted into a different type of element in the resulting merged
         * manifest.
         */
        CONVERTED,
    }

    /**
     * Defines an abstract record contain common metadata for elements and attributes actions.
     */
    public abstract static class Record {

        @NotNull protected final ActionType mActionType;
        @NotNull protected final SourceFilePosition mActionLocation;
        @NotNull protected final XmlNode.NodeKey mTargetId;
        @Nullable protected final String mReason;

        private Record(@NotNull ActionType actionType,
                @NotNull SourceFilePosition actionLocation,
                @NotNull XmlNode.NodeKey targetId,
                @Nullable String reason) {
            mActionType = Preconditions.checkNotNull(actionType);
            mActionLocation = Preconditions.checkNotNull(actionLocation);
            mTargetId = Preconditions.checkNotNull(targetId);
            mReason = reason;
        }

        @NotNull
        public ActionType getActionType() {
            return mActionType;
        }

        @NotNull
        public SourceFilePosition getActionLocation() {
            return mActionLocation;
        }

        @NotNull
        public XmlNode.NodeKey getTargetId() {
            return mTargetId;
        }

        @Nullable
        public String getReason() {
            return mReason;
        }

        public void print(@NotNull StringBuilder stringBuilder) {
            stringBuilder.append(mActionType)
                    .append(" from ")
                    .append(mActionLocation);
            if (mReason != null) {
                stringBuilder.append(" reason: ")
                        .append(mReason);
            }
        }
    }

    /**
     * Defines a merging tool action for an xml element.
     */
    public static class NodeRecord extends Record {

        @NotNull
        private final NodeOperationType mNodeOperationType;

        NodeRecord(@NotNull ActionType actionType,
                @NotNull SourceFilePosition actionLocation,
                @NotNull XmlNode.NodeKey targetId,
                @Nullable String reason,
                @NotNull NodeOperationType nodeOperationType) {
            super(actionType, actionLocation, targetId, reason);
            this.mNodeOperationType = Preconditions.checkNotNull(nodeOperationType);
        }

        @NotNull
        @Override
        public String toString() {
            return "Id=" + mTargetId.toString() + " actionType=" + getActionType()
                    + " location=" + getActionLocation()
                    + " opType=" + mNodeOperationType;
        }
    }

    /**
     * Defines a merging tool action for an xml attribute
     */
    public static class AttributeRecord extends Record {

        // first in wins which should be fine, the first
        // operation type will be the highest priority one
        @Nullable
        private final AttributeOperationType mOperationType;

        AttributeRecord(
                @NotNull ActionType actionType,
                @NotNull SourceFilePosition actionLocation,
                @NotNull XmlNode.NodeKey targetId,
                @Nullable String reason,
                @Nullable AttributeOperationType operationType) {
            super(actionType, actionLocation, targetId, reason);
            this.mOperationType = operationType;
        }

        @Nullable
        public AttributeOperationType getOperationType() {
            return mOperationType;
        }

        @NotNull
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("Id", mTargetId)
                    .add("actionType=",getActionType())
                    .add("location", getActionLocation())
                    .add("opType", getOperationType()).toString();
        }
    }

    @NotNull
    public String persist() {
        //noinspection SpellCheckingInspection
        GsonBuilder gson = new GsonBuilder().setPrettyPrinting();
        gson.enableComplexMapKeySerialization();
        MessageJsonSerializer.registerTypeAdapters(gson);
        return gson.create().toJson(this);
    }

    @Nullable
    public static Actions load(@NotNull InputStream inputStream) {

        return getGsonParser().fromJson(new InputStreamReader(inputStream), Actions.class);
    }

    private static class NodeNameDeserializer implements JsonDeserializer<XmlNode.NodeName> {

        @Override
        public XmlNode.NodeName deserialize(@NotNull JsonElement json, Type typeOfT,
                @NotNull JsonDeserializationContext context) throws JsonParseException {
            if (json.getAsJsonObject().get("mNamespaceURI") != null) {
                return context.deserialize(json, XmlNode.NamespaceAwareName.class);
            } else {
                return context.deserialize(json, XmlNode.Name.class);
            }
        }
    }

    @Nullable
    public static Actions load(String xml) {
        return getGsonParser().fromJson(xml, Actions.class);
    }

    @SuppressWarnings("SpellCheckingInspection")
    @NotNull
    private static Gson getGsonParser() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.enableComplexMapKeySerialization();
        gsonBuilder.registerTypeAdapter(XmlNode.NodeName.class, new NodeNameDeserializer());
        MessageJsonSerializer.registerTypeAdapters(gsonBuilder);
        return gsonBuilder.create();
    }

    public ImmutableMultimap<Integer, Record> getResultingSourceMapping(@NotNull XmlDocument xmlDocument)
            throws ParserConfigurationException, SAXException, IOException {

        SourceFile inMemory = SourceFile.UNKNOWN;

        XmlDocument loadedWithLineNumbers =
                XmlLoader.load(
                        xmlDocument.getSelectors(),
                        xmlDocument.getSystemPropertyResolver(),
                        inMemory,
                        xmlDocument.prettyPrint(),
                        XmlDocument.Type.MAIN,
                        null, /* mainManifestPackageName */
                        xmlDocument.getModel());

        ImmutableMultimap.Builder<Integer, Record> mappingBuilder = ImmutableMultimap.builder();
        for (XmlElement xmlElement : loadedWithLineNumbers.getRootNode().getMergeableElements()) {
            parse(xmlElement, mappingBuilder);
        }
        return mappingBuilder.build();
    }

    private void parse(@NotNull XmlElement element,
            @NotNull ImmutableMultimap.Builder<Integer, Record> mappings) {
        DecisionTreeRecord decisionTreeRecord = mRecords.get(element.getId());
        if (decisionTreeRecord != null) {
            Actions.NodeRecord nodeRecord = findNodeRecord(decisionTreeRecord);
            if (nodeRecord != null) {
                mappings.put(element.getPosition().getStartLine(), nodeRecord);
            }
            for (XmlAttribute xmlAttribute : element.getAttributes()) {
                Actions.AttributeRecord attributeRecord = findAttributeRecord(decisionTreeRecord,
                        xmlAttribute);
                if (attributeRecord != null) {
                    mappings.put(xmlAttribute.getPosition().getStartLine(), attributeRecord);
                }
            }
        }
        for (XmlElement xmlElement : element.getMergeableElements()) {
            parse(xmlElement, mappings);
        }
    }

    @NotNull
    public String blame(@NotNull XmlDocument xmlDocument)
            throws IOException, SAXException, ParserConfigurationException {

        ImmutableMultimap<Integer, Record> resultingSourceMapping =
                getResultingSourceMapping(xmlDocument);
        LineReader lineReader = new LineReader(
                new StringReader(xmlDocument.prettyPrint()));

        StringBuilder actualMappings = new StringBuilder();
        String line;
        int count = 0;
        while ((line = lineReader.readLine()) != null) {
            actualMappings.append(count + 1).append(line).append("\n");
            if (resultingSourceMapping.containsKey(count)) {
                for (Record record : resultingSourceMapping.get(count)) {
                    actualMappings.append(count + 1).append("-->")
                            .append(record.getActionLocation().toString())
                            .append("\n");
                }
            }
            count++;
        }
        return actualMappings.toString();
    }

    @Nullable
    private static Actions.NodeRecord findNodeRecord(@NotNull DecisionTreeRecord decisionTreeRecord) {
        for (Actions.NodeRecord nodeRecord : decisionTreeRecord.getNodeRecords()) {
            if (nodeRecord.getActionType() == Actions.ActionType.ADDED) {
                return nodeRecord;
            }
        }
        return null;
    }

    @Nullable
    public Actions.NodeRecord findNodeRecord(@NotNull XmlNode.NodeKey nodeKey) {
        for (Actions.NodeRecord nodeRecord : getNodeRecords(nodeKey)) {
            if (nodeRecord.getActionType() == Actions.ActionType.ADDED) {
                return nodeRecord;
            }
        }
        return null;
    }

    @Nullable
    private static Actions.AttributeRecord findAttributeRecord(
            @NotNull DecisionTreeRecord decisionTreeRecord,
            @NotNull XmlAttribute xmlAttribute) {
        for (Actions.AttributeRecord attributeRecord : decisionTreeRecord
                .getAttributeRecords(xmlAttribute.getName())) {
            if (attributeRecord.getActionType() == Actions.ActionType.ADDED) {
                return attributeRecord;
            }
        }
        return null;
    }

    @Nullable
    public Actions.AttributeRecord findAttributeRecord(
            @NotNull XmlNode.NodeKey nodeKey, @NotNull XmlNode.NodeName attributeName) {
        for (Actions.AttributeRecord attributeRecord :
                getAttributeRecords(nodeKey, attributeName)) {
            if (attributeRecord.getActionType() == Actions.ActionType.ADDED) {
                return attributeRecord;
            }
        }
        return null;
    }

    /**
     * Internal structure on how {@link com.android.manifmerger.Actions.Record}s are kept for an
     * xml element.
     *
     * Each xml element should have an associated DecisionTreeRecord which keeps a list of
     * {@link com.android.manifmerger.Actions.NodeRecord} for all the node actions related
     * to this xml element.
     *
     * It will also contain a map indexed by attribute name on all the attribute actions related
     * to that particular attribute within the xml element.
     *
     */
    static class DecisionTreeRecord {
        // all other occurrences of the nodes decisions, in order of decisions.
        private final List<NodeRecord> mNodeRecords = new ArrayList<>();

        // all attributes decisions indexed by attribute name.
        @NotNull
        final Map<XmlNode.NodeName, List<AttributeRecord>> mAttributeRecords = new HashMap<>();

        @NotNull
        ImmutableList<NodeRecord> getNodeRecords() {
            return ImmutableList.copyOf(mNodeRecords);
        }

        @NotNull
        ImmutableMap<XmlNode.NodeName, List<AttributeRecord>> getAttributesRecords() {
            return ImmutableMap.copyOf(mAttributeRecords);
        }

        DecisionTreeRecord() {
        }

        void addNodeRecord(@NotNull NodeRecord nodeRecord) {
            mNodeRecords.add(Preconditions.checkNotNull(nodeRecord));
        }

        @NotNull
        ImmutableList<AttributeRecord> getAttributeRecords(XmlNode.NodeName attributeName) {
            List<AttributeRecord> attributeRecords = mAttributeRecords.get(attributeName);
            return attributeRecords == null
                    ? ImmutableList.of()
                    : ImmutableList.copyOf(attributeRecords);
        }
    }
}
