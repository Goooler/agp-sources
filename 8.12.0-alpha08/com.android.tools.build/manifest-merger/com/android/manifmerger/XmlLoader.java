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

import static com.android.manifmerger.PlaceholderHandler.KeyBasedValueResolver;

import com.android.ide.common.blame.SourceFile;
import com.android.resources.NamespaceReferenceRewriter;
import com.android.utils.PositionXmlParser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Responsible for loading XML files.
 */
public final class XmlLoader {

    private XmlLoader() {}

    /**
     * Loads an xml file without doing xml validation and return a {@link XmlDocument}
     *
     * @param displayName the xml file display name.
     * @param xmlFile the xml file.
     * @param namespace the namespace, used to create or shorten fully qualified class names. If
     *     null, the manifest's package name is used as the namespace instead.
     * @return the initialized {@link XmlDocument}
     */
    @NotNull
    public static XmlDocument load(
            @NotNull KeyResolver<String> selectors,
            @NotNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertyResolver,
            @Nullable String displayName,
            @NotNull File xmlFile,
            @NotNull InputStream inputStream,
            @NotNull XmlDocument.Type type,
            @Nullable String namespace,
            @NotNull DocumentModel<ManifestModel.NodeTypes> model,
            boolean rewriteNamespaces)
            throws IOException, SAXException, ParserConfigurationException {
        Document domDocument = PositionXmlParser.parse(inputStream);
        return load(
                domDocument,
                selectors,
                systemPropertyResolver,
                displayName,
                xmlFile,
                type,
                namespace,
                model,
                rewriteNamespaces);
    }

    /**
     * Build an unvalidated {@link XmlDocument} object from given DOM Document Also see overload
     * {@link XmlLoader#load(KeyResolver, KeyBasedValueResolver, String, File, InputStream,
     * XmlDocument.Type, String, DocumentModel, boolean)} for more details.
     *
     * @param domDocument Manifest Document object.
     */
    @NotNull
    public static XmlDocument load(
            @NotNull Document domDocument,
            @NotNull KeyResolver<String> selectors,
            @NotNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertyResolver,
            @Nullable String displayName,
            @NotNull File xmlFile,
            @NotNull XmlDocument.Type type,
            @Nullable String namespace,
            @NotNull DocumentModel<ManifestModel.NodeTypes> model,
            boolean rewriteNamespaces)
            throws IOException, SAXException, ParserConfigurationException {
        Element rootElement = domDocument.getDocumentElement();
        @Nullable
        final String namespaceOrPackageName =
                namespace != null ? namespace : rootElement.getAttribute("package");
        if (rewriteNamespaces) {
            new NamespaceReferenceRewriter(
                            namespaceOrPackageName, (String t, String n) -> namespaceOrPackageName)
                    .rewriteManifestNode(rootElement, true);
        }
        return new XmlDocument(
                new SourceFile(xmlFile, displayName),
                selectors,
                systemPropertyResolver,
                domDocument.getDocumentElement(),
                type,
                namespaceOrPackageName,
                model);
    }

    /**
     * Loads a xml document from its {@link String} representation without doing xml validation and
     * return a {@link XmlDocument}
     *
     * @param sourceFile the source location to use for logging and record collection.
     * @param xml the persisted xml.
     * @param namespace the namespace, used to create or shorten fully qualified class names. If
     *     null, the manifest's package name is used as the namespace instead.
     * @return the initialized {@link XmlDocument}
     * @throws IOException this should never be thrown.
     * @throws SAXException if the xml is incorrect
     * @throws ParserConfigurationException if the xml engine cannot be configured.
     */
    @NotNull
    public static XmlDocument load(
            @NotNull KeyResolver<String> selectors,
            @NotNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertyResolver,
            @NotNull SourceFile sourceFile,
            @NotNull String xml,
            @NotNull XmlDocument.Type type,
            @Nullable String namespace,
            @NotNull DocumentModel<ManifestModel.NodeTypes> model)
            throws IOException, SAXException, ParserConfigurationException {
        Document domDocument = PositionXmlParser.parse(xml);
        Element rootElement = domDocument.getDocumentElement();
        @Nullable
        final String namespaceOrPackageName =
                namespace != null ? namespace : rootElement.getAttribute("package");
        return new XmlDocument(
                sourceFile,
                selectors,
                systemPropertyResolver,
                domDocument.getDocumentElement(),
                type,
                namespaceOrPackageName,
                model);
    }
}
