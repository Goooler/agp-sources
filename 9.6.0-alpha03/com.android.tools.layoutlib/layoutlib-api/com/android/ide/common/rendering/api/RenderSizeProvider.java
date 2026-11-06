/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import java.awt.Dimension;

/**
 * Interface to negotiate the target render dimensions. Allows the IDE to apply quality downscaling
 * and size constraints.
 */
@FunctionalInterface
public interface RenderSizeProvider {
    /**
     * Returns the target layout rendering dimensions.
     *
     * @param width the requested width of the layout
     * @param height the requested height of the layout
     * @return the negotiated dimensions for the image.
     */
    Dimension getTargetSize(int width, int height);
}
