/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.services.DslServices;
import com.android.builder.core.ComponentType;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.model.ObjectFactory;

/**
 * Factory to create DeclarativeBuildType object using an {@link ObjectFactory} to add the DSL
 * methods.
 */
public class DeclarativeBuildTypeFactory implements NamedDomainObjectFactory<DeclarativeBuildType> {

    @NonNull private final DslServices dslServices;
    @NonNull private final ComponentType componentType;

    public DeclarativeBuildTypeFactory(
            @NonNull DslServices dslServices, @NonNull ComponentType componentType) {
        this.dslServices = dslServices;
        this.componentType = componentType;
    }

    @NonNull
    @Override
    public DeclarativeBuildType create(@NonNull String name) {
        return dslServices.newDecoratedInstance(
                DeclarativeBuildType.class, name, dslServices, componentType);
    }
}
