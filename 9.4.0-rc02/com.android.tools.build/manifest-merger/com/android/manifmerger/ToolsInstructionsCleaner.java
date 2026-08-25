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

import static com.android.manifmerger.MergingReport.Result.ERROR;

import com.android.SdkConstants;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.ILogger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import kotlin.Pair;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes all "tools:" statements from the resulting xml.
 *
 * All attributes belonging to the {@link com.android.SdkConstants#ANDROID_URI} namespace will be
 * removed. If an element contained a "tools:node=\"remove\"" attribute, the element will be
 * deleted. And elements that are themselves in the tools namespace will also be removed.
 */
@Immutable
public class ToolsInstructionsCleaner {

    private static final String REMOVE_OPERATION_XML_MAME =
            NodeOperationType.REMOVE.toCamelCaseName();
    private static final String REMOVE_ALL_OPERATION_XML_MAME =
            NodeOperationType.REMOVE_ALL.toCamelCaseName();

    /**
     * Cleans all attributes belonging to the {@link com.android.SdkConstants#TOOLS_URI} namespace.
     *
     * @param document the document to clean
     * @param logger logger to use in case of errors and warnings.
     * @return the cleaned document or null if an error occurred.
     */
    public static Optional<Document> cleanToolsReferences(
            @NotNull ManifestMerger2.MergeType mergeType,
            @NotNull XmlDocument document,
            @NotNull ILogger logger) {

        Preconditions.checkNotNull(document);
        Preconditions.checkNotNull(logger);
        MergingReport.Result result =
                cleanToolsReferences(mergeType, null, document.getRootNode(), logger).getFirst();
        if (result == MergingReport.Result.SUCCESS) {
            return Optional.of(document.getXml());
        }
        return Optional.absent();
    }

    @NotNull
    private static Pair<MergingReport.Result, Boolean> cleanToolsReferences(
            @NotNull ManifestMerger2.MergeType mergeType,
            @Nullable XmlElement parent,
            @NotNull XmlElement element,
            @NotNull ILogger logger) {

        if (SdkConstants.TOOLS_URI.equals(element.getXml().getNamespaceURI())) {
            // Delete the entire node
            if (parent == null) {
                logger.error(
                        null /* Throwable */,
                        String.format(
                                "tools namespace not allowed on top level %s element",
                                element.getName().getLocalName()));
                return new Pair<>(ERROR, false);
            }
            parent.removeChild(element);
            return new Pair<>(MergingReport.Result.SUCCESS, false);
        }

        NamedNodeMap namedNodeMap = element.getXml().getAttributes();
        if (namedNodeMap != null) {
            Node nodeOperation =
                    namedNodeMap.getNamedItemNS(
                            SdkConstants.TOOLS_URI, NodeOperationType.NODE_LOCAL_NAME);
            if (nodeOperation != null) {
                String operationValue = nodeOperation.getNodeValue();
                boolean hasSelector =
                        namedNodeMap.getNamedItemNS(SdkConstants.TOOLS_URI, "selector") != null;
                if (operationValue.equals(REMOVE_ALL_OPERATION_XML_MAME)
                        || (operationValue.equals(REMOVE_OPERATION_XML_MAME) && !hasSelector)) {

                    if (parent == null) {
                        logger.error(
                                null /* Throwable */,
                                String.format(
                                        "tools:node=\"%1$s\" not allowed on top level %2$s"
                                                + " element",
                                        operationValue, element.getName().getLocalName()));
                        return new Pair<>(ERROR, false);
                    } else {
                        // Remove leading comments
                        for (Node comment : XmlElement.getLeadingComments(element.getXml())) {
                            parent.removeChild(comment);
                        }

                        parent.removeChild(element);
                        return new Pair<>(MergingReport.Result.SUCCESS, false);
                    }
                }
            }
        }

        boolean needsToolsNamespace = false;
        // make a copy of the element children since we will be removing some during
        // this process, we don't want side effects.
        ImmutableList<XmlElement> childElements =
                ImmutableList.copyOf(element.getMergeableElements());
        for (XmlElement childElement : childElements) {
            Pair<MergingReport.Result, Boolean> result =
                    cleanToolsReferences(mergeType, element, childElement, logger);
            needsToolsNamespace |= result.getSecond();
            if (result.getFirst() == ERROR) {
                return new Pair<>(ERROR, needsToolsNamespace);
            }
        }

        if (namedNodeMap != null) {
            // make a copy of the original list of attributes as we will remove some during this
            // process.
            List<Node> attributes = new ArrayList<Node>();
            for (int i = 0; i < namedNodeMap.getLength(); i++) {
                attributes.add(namedNodeMap.item(i));
            }
            for (Node attribute : attributes) {
                if (SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {
                    // anything else, we just clean the attribute unless we are merging for
                    // libraries.
                    if (mergeType.isKeepToolsAttributeRequired(
                            attribute.getLocalName(), attribute.getNodeValue())) {
                        namedNodeMap.removeNamedItemNS(
                                attribute.getNamespaceURI(), attribute.getLocalName());
                    } else {
                        needsToolsNamespace = true;
                    }
                }
                // this could also be the xmlns:tools declaration.
                if (attribute.getNodeName().startsWith(SdkConstants.XMLNS_PREFIX)
                        && SdkConstants.TOOLS_URI.equals(attribute.getNodeValue())
                        && !needsToolsNamespace) {
                    namedNodeMap.removeNamedItem(attribute.getNodeName());
                }
            }
        }

        return new Pair<>(MergingReport.Result.SUCCESS, needsToolsNamespace);
    }

}
