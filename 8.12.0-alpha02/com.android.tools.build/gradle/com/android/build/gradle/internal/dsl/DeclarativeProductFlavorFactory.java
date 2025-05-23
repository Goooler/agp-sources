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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.model.ObjectFactory;

/**
 * Factory to create DeclarativeProductFlavor object using an {@link ObjectFactory} to add the DSL
 * methods.
 */
public class DeclarativeProductFlavorFactory
        implements NamedDomainObjectFactory<DeclarativeProductFlavor> {

    @NonNull private final DslServices dslServices;

    public DeclarativeProductFlavorFactory(@NonNull DslServices dslServices) {
        this.dslServices = dslServices;
    }

    @NonNull
    @Override
    public DeclarativeProductFlavor create(@NonNull String name) {
        return dslServices.newDecoratedInstance(DeclarativeProductFlavor.class, name, dslServices);
    }
}
