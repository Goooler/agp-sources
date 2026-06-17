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

import java.awt.image.BufferedImage;

/**
 * A wrapper/container for a rendered layout image whose lifecycle is managed via an ownership
 * transfer.
 *
 * <p>When Layoutlib renders a frame, it transfers logical ownership of the recyclable image to the
 * client (e.g., Android Studio). When the client is done displaying or copying the image, they MUST
 * call {@link #close()} to return the backing physical buffer to Layoutlib's internal reuse pool.
 *
 * <p>After calling {@link #close()}, the {@link BufferedImage} returned by {@link #getImage()} is
 * considered invalid and must not be used or drawn.
 */
public interface RecyclableImage extends AutoCloseable {
    /** Returns the logical width of the rendered layout. */
    int getWidth();

    /** Returns the logical height of the rendered layout. */
    int getHeight();

    /** Returns the {@link BufferedImage} containing the rendered pixels. */
    BufferedImage getImage();

    /** Closes the image and returns the backing physical buffer to Layoutlib. */
    @Override
    void close();
}
