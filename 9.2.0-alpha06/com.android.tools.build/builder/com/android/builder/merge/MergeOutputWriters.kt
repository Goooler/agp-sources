/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.builder.merge

import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.utils.FileUtils
import com.android.zipflinger.Archive
import com.android.zipflinger.BytesSource
import com.android.zipflinger.Source
import com.android.zipflinger.StableArchive
import com.android.zipflinger.ZipArchive
import com.google.common.base.Preconditions
import com.google.common.io.ByteStreams
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.util.zip.Deflater

/** Factory methods for [MergeOutputWriter]. */
object MergeOutputWriters {

  /**
   * Creates a writer that writes files to a directory.
   *
   * @param directory the directory; will be created if it doesn't exist
   * @return the writer
   */
  @JvmStatic
  fun toDirectory(directory: File): MergeOutputWriter {
    /*
     * In theory we could just create the directory here. However, some tasks in gradle fail
     * if we create an empty directory for very obscure reasons. To avoid those errors, we
     * delay directory creation until it is really necessary.
     */

    val directoryPath = directory.toPath()

    return object : MergeOutputWriter {
      /** Is the writer open? */
      private var isOpen = false

      /** Have we ensured that the directory has been created? */
      private var created = false

      override fun open() {
        Preconditions.checkState(!isOpen, "Writer already open")
        isOpen = true
      }

      override fun close() {
        Preconditions.checkState(isOpen, "Writer closed")
        isOpen = false
      }

      /**
       * Converts a path to the file, resolving it against the `directoryUri`.
       *
       * @param path the path
       * @return the resolved file
       */
      fun toFile(path: String): File {
        if (!created) {
          FileUtils.mkdirs(directory)
          created = true
        }

        return directoryPath.resolve(path).toFile()
      }

      override fun remove(path: String) {
        Preconditions.checkState(isOpen, "Writer closed")

        val f = toFile(path)
        // it's possible that some folders only containing .class files got removed.
        // those were never merged in so we just ignore removing a non existent folder.
        if (!f.exists()) return

        // since we are notified of folders add/remove by the transform pipeline, handle
        // folders and files.
        if (f.isDirectory) {
          try {
            FileUtils.deletePath(f)
          } catch (e: IOException) {
            throw UncheckedIOException(e)
          }
          return
        }

        if (!f.delete()) {
          throw UncheckedIOException(IOException("Cannot delete file " + f.getAbsolutePath()))
        }

        var dir = f.parentFile
        while (dir.toPath() != directory.toPath()) {
          val names: Array<String?> = checkNotNull(dir.list())
          if (names.isEmpty()) {
            try {
              FileUtils.delete(dir)
            } catch (e: IOException) {
              throw UncheckedIOException(e)
            }
          } else {
            break
          }
          dir = dir.parentFile
        }
      }

      override fun create(path: String, data: InputStream, compress: Boolean) {
        Preconditions.checkState(isOpen, "Writer closed")

        val f = toFile(path)
        FileUtils.mkdirs(f.parentFile)

        try {
          FileOutputStream(f).use { fos -> ByteStreams.copy(data, fos) }
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      override fun replace(path: String, data: InputStream, compress: Boolean) {
        // Create implementation overrides
        create(path, data, compress)
      }
    }
  }

  /**
   * Creates a writer that writes files to a zip file.
   *
   * @param file the existing zip file
   * @return the writer
   */
  @JvmStatic
  @Deprecated("Use toZipWithZipFlinger instead")
  fun toZip(file: File, zFileOptions: ZFileOptions): MergeOutputWriter {
    return object : MergeOutputWriter {
      /** The open zip file, `null` if not open. */
      private var zipFile: ZFile? = null

      override fun open() {
        Preconditions.checkState(zipFile == null, "Writer already open")

        try {
          zipFile = ZFile.openReadWrite(file, zFileOptions)
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      override fun close() {
        Preconditions.checkState(zipFile != null, "Writer not open")

        try {
          zipFile!!.close()
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        } finally {
          zipFile = null
        }
      }

      override fun remove(path: String) {
        Preconditions.checkState(zipFile != null, "Writer not open")

        val entry = zipFile!!.get(path)
        if (entry != null) {
          try {
            entry.delete()
          } catch (e: IOException) {
            throw UncheckedIOException(e)
          }
        }
      }

      override fun create(path: String, data: InputStream, compress: Boolean) {
        Preconditions.checkState(zipFile != null, "Writer not open")

        try {
          zipFile!!.add(path, data, compress)
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      override fun replace(path: String, data: InputStream, compress: Boolean) {
        Preconditions.checkState(zipFile != null, "Writer not open")

        try {
          zipFile!!.add(path, data, compress)
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }
    }
  }

  /**
   * Creates a writer that writes files to a zip file using ZipFlinger.
   *
   * @param file the existing zip file
   * @return the writer
   */
  @JvmStatic
  fun toZipWithZipFlinger(file: File): SourceMergeOutputWriter {
    return object : SourceMergeOutputWriter {
      private var archive: Archive? = null

      override fun open() {
        Preconditions.checkState(archive == null, "Writer already open")
        try {
          // Use StableArchive to for deterministic ordering and timestamp removal.
          archive = StableArchive(ZipArchive(file.toPath()))
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      @Throws(IOException::class)
      override fun close() {
        Preconditions.checkState(archive != null, "Writer not open")
        archive!!.close()
        archive = null
      }

      override fun remove(path: String) {
        Preconditions.checkState(archive != null, "Writer not open")
        try {
          archive!!.delete(path)
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      override fun create(path: String, data: InputStream, compress: Boolean) {
        val source =
          BytesSource(ByteStreams.toByteArray(data), path, if (compress) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
        create(path, source)
      }

      override fun create(path: String, source: Source) {
        Preconditions.checkState(archive != null, "Writer not open")
        try {
          archive!!.add(source)
        } catch (e: IOException) {
          throw UncheckedIOException(e)
        }
      }

      override fun replace(path: String, data: InputStream, compress: Boolean) {
        remove(path)
        create(path, data, compress)
      }

      override fun replace(path: String, source: Source) {
        remove(path)
        create(path, source)
      }
    }
  }
}
