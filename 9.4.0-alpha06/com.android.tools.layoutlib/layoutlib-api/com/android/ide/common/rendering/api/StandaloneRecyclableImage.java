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
 * A standalone, in-memory implementation of {@link RecyclableImage} backed by a {@link
 * BufferedImage}.
 */
class StandaloneRecyclableImage implements RecyclableImage {
    private BufferedImage myImage;
    private final int myWidth;
    private final int myHeight;

    StandaloneRecyclableImage(BufferedImage image) {
        myImage = image;
        myWidth = myImage.getWidth();
        myHeight = myImage.getHeight();
    }

    @Override
    public int getWidth() {
        return myWidth;
    }

    @Override
    public int getHeight() {
        return myHeight;
    }

    @Override
    public boolean isValid() {
        return myImage != null;
    }

    @Override
    public void drawImageTo(
            Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
        assertIsValid();
        g.drawImage(myImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    @Override
    public void paint(Consumer<Graphics2D> command) {
        assertIsValid();
        Graphics2D g = myImage.createGraphics();
        try {
            command.accept(g);
        } finally {
            g.dispose();
        }
    }

    @Override
    public BufferedImage getCopy(int x, int y, int w, int h) {
        assertIsValid();
        BufferedImage toCopy;
        if (x == 0 && y == 0 && w == getWidth() && h == getHeight()) {
            toCopy = myImage;
        } else {
            toCopy = myImage.getSubimage(x, y, w, h);
        }
        WritableRaster raster =
                toCopy.copyData(toCopy.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(
                toCopy.getColorModel(), raster, toCopy.isAlphaPremultiplied(), null);
    }

    @Override
    public void close() {
        myImage = null;
    }

    private void assertIsValid() {
        if (!isValid()) {
            throw new IllegalStateException("This image has already been closed");
        }
    }
}
