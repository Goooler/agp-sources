/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Information containing a feature split declaration that can be consumed by other modules as
 * persisted json file
 */
public class FeatureSplitDeclaration {

    public static final String PERSISTED_FILE_NAME = "feature-split.json";

    @NonNull private final String modulePath;
    @NonNull private final String namespace;
    @Nullable private final String buildType;
    @NonNull private final Map<String, String> productFlavors;

    public FeatureSplitDeclaration(
            @NonNull String modulePath,
            @NonNull String namespace,
            @Nullable String buildType,
            @NonNull Map<String, String> productFlavors) {
        this.modulePath = modulePath;
        this.namespace = namespace;
        this.buildType = buildType;
        this.productFlavors = productFlavors;
    }

    public FeatureSplitDeclaration(@NonNull String modulePath, @NonNull String namespace) {
        this(modulePath, namespace, null, Collections.emptyMap());
    }

    @NonNull
    public String getModulePath() {
        return modulePath;
    }

    @NonNull
    public String getNamespace() {
        return namespace;
    }

    @Nullable
    public String getBuildType() {
        return buildType;
    }

    @NonNull
    public Map<String, String> getProductFlavors() {
        return productFlavors == null ? Collections.emptyMap() : productFlavors;
    }

    public void save(@NonNull File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, PERSISTED_FILE_NAME);
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
        }
    }

    @NonNull
    public static FeatureSplitDeclaration load(@NonNull FileCollection input) throws IOException {
        File persistedFile = getOutputFile(input);
        if (persistedFile == null) {
            throw new FileNotFoundException("No feature split declaration present");
        }
        return load(persistedFile);
    }

    @NonNull
    public static FeatureSplitDeclaration load(@NonNull File input) throws IOException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        try (FileReader reader = new FileReader(input, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, FeatureSplitDeclaration.class);
        }
    }

    @Nullable
    private static File getOutputFile(@NonNull FileCollection input) {
        for (File file : input.getAsFileTree().getFiles()) {
            if (file.getName().equals(PERSISTED_FILE_NAME)) {
                return file;
            }
        }
        return null;
    }

    @NonNull
    public static File getOutputFile(@NonNull File directory) {
        return new File(directory, PERSISTED_FILE_NAME);
    }
}
