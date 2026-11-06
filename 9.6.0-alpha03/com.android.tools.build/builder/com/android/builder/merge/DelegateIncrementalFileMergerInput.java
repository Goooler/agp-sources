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

package com.android.builder.merge;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.FileStatus;
import com.google.common.collect.ImmutableSet;

/**
 * {@link IncrementalFileMergerInput} that delegates all operations to another
 * {@link IncrementalFileMergerInput}. This can be used as a base class for extending inputs.
 * This class will delegate all methods to the delegate input.
 */
public class DelegateIncrementalFileMergerInput extends DelegateFileMergerInput implements IncrementalFileMergerInput {

    /**
     * The instance to delegate to.
     */
    @NonNull
    private final IncrementalFileMergerInput delegate;

    /**
     * Creates a new input.
     *
     * @param incrementalDelegate the delegate calls to
     */
    public DelegateIncrementalFileMergerInput(@NonNull IncrementalFileMergerInput incrementalDelegate) {
        super(incrementalDelegate);
        this.delegate = incrementalDelegate;
    }

    @NonNull
    @Override
    public ImmutableSet<String> getUpdatedPaths() {
        return ImmutableSet.copyOf(delegate.getUpdatedPaths());
    }

    @Override
    public FileStatus getFileStatus(String path) {
        return delegate.getFileStatus(path);
    }
}
