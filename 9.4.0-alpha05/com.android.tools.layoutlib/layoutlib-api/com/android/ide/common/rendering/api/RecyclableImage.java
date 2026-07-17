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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.function.Consumer;

/**
 * A wrapper/container for a rendered layout image whose lifecycle is managed via an ownership
 * transfer.
 *
 * <p>When Layoutlib renders a frame, it transfers logical ownership of the recyclable image to the
 * client (e.g., Android Studio). When the client is done displaying or copying the image, they MUST
 * call {@link #close()} to return the backing physical buffer to Layoutlib's internal reuse pool.
 *
 * <p>To prevent callers from retaining references to physical buffers after they have been
 * recycled, this interface encapsulates pixel access via functional painting and copying
 * primitives.
 */
public interface RecyclableImage extends AutoCloseable {
    /** An empty, invalid image representing the absence of a rendered layout frame. */
    RecyclableImage NULL =
            new RecyclableImage() {
                @Override
                public int getWidth() {
                    return 0;
                }

                @Override
                public int getHeight() {
                    return 0;
                }

                @Override
                public BufferedImage getImage() {
                    return null;
                }

                @Override
                public void drawImageTo(
                        Graphics g,
                        int dx1,
                        int dy1,
                        int dx2,
                        int dy2,
                        int sx1,
                        int sy1,
                        int sx2,
                        int sy2) {}

                @Override
                public void paint(Consumer<Graphics2D> command) {}

                @Override
                public BufferedImage getCopy(int x, int y, int w, int h) {
                    return null;
                }

                @Override
                public boolean isValid() {
                    return false;
                }

                @Override
                public void close() {}
            };

    /** Returns the logical width of the rendered layout. */
    int getWidth();

    /** Returns the logical height of the rendered layout. */
    int getHeight();

    /**
     * Returns the {@link BufferedImage} containing the rendered pixels.
     *
     * @deprecated Use functional drawing/copying methods instead. Kept for layoutlib transition.
     */
    @Deprecated
    BufferedImage getImage();

    /** Returns true if the image is valid and has not been closed or recycled. */
    default boolean isValid() {
        return getImage() != null;
    }

    /** Draws the current image to the given {@link Graphics} context. */
    default void drawImageTo(
            Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
        BufferedImage img = getImage();
        if (img == null) {
            throw new IllegalArgumentException("This image has already been closed");
        }
        g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    /**
     * Allows painting into the image. The passed {@link Graphics2D} context will be disposed right
     * after this call finishes, so do not keep a reference to it.
     */
    default void paint(Consumer<Graphics2D> command) {
        BufferedImage img = getImage();
        if (img == null) {
            throw new IllegalArgumentException("This image has already been closed");
        }
        Graphics2D g = img.createGraphics();
        try {
            command.accept(g);
        } finally {
            g.dispose();
        }
    }

    /** Returns a {@link BufferedImage} with a copy of a sub-image of the rendered frame. */
    default BufferedImage getCopy(int x, int y, int w, int h) {
        BufferedImage img = getImage();
        if (img == null) {
            throw new IllegalArgumentException("This image has already been closed");
        }
        BufferedImage toCopy;
        if (x == 0 && y == 0 && w == getWidth() && h == getHeight()) {
            toCopy = img;
        } else {
            toCopy = img.getSubimage(x, y, w, h);
        }
        WritableRaster raster =
                toCopy.copyData(toCopy.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(
                toCopy.getColorModel(), raster, toCopy.isAlphaPremultiplied(), null);
    }

    /** Draws the current image to the given {@link Graphics} context. */
    default void drawImageTo(Graphics g, int x, int y, int w, int h) {
        drawImageTo(g, x, y, x + w, y + h, 0, 0, getWidth(), getHeight());
    }

    /** Returns a {@link BufferedImage} with a copy of the rendered frame. */
    default BufferedImage getCopy() {
        return getCopy(0, 0, getWidth(), getHeight());
    }

    /**
     * Creates a {@link RecyclableImage} backed by a new {@link BufferedImage} with the given
     * dimensions and type.
     */
    static RecyclableImage create(int w, int h, int type) {
        return create(new BufferedImage(w, h, type));
    }

    /** Creates a {@link RecyclableImage} backed by the given {@link BufferedImage}. */
    static RecyclableImage create(BufferedImage image) {
        return new StandaloneRecyclableImage(image);
    }

    /** Creates a standalone copy of the given {@link RecyclableImage}. */
    static RecyclableImage copyOf(RecyclableImage image) {
        BufferedImage copy = image.getCopy();
        return create(copy);
    }

    static BufferedImage copy(BufferedImage originalImage) {
        WritableRaster raster =
                originalImage.copyData(originalImage.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(
                originalImage.getColorModel(), raster, originalImage.isAlphaPremultiplied(), null);
    }

    /** Closes the image and returns the backing physical buffer to Layoutlib. */
    @Override
    void close();
}
