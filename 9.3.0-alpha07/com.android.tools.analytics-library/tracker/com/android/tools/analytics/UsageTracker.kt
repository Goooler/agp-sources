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
package com.android.tools.analytics

import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.TestOnly

/**
 * UsageTracker is an api to report usage of features. This data is used to improve future versions of Android Studio and related tools.
 *
 * The tracker has an API to logDetails usage (in the form of protobuf messages). A separate system called the Analytics Publisher takes the
 * logs and sends them to Google's servers for analysis.
 */
object UsageTracker {
  private data class Writers(val anonymous: UsageTrackerWriter, val loggedIn: UsageTrackerWriter) : AutoCloseable {
    override fun close() {
      var exception: Exception? = null
      try {
        anonymous.flush()
      } catch (e: Exception) {
        exception = e
      }

      try {
        anonymous.close()
      } catch (e: Exception) {
        exception = exception ?: e
      }

      try {
        loggedIn.flush()
      } catch (e: Exception) {
        exception = exception ?: e
      }

      try {
        loggedIn.close()
      } catch (e: Exception) {
        exception = exception ?: e
      }

      if (exception != null) {
        throw RuntimeException("Unable to close usage tracker", exception)
      }
    }
  }

  enum class Mode {
    // Logging will throw, indicating a bug in the calling code.
    UNINITIALIZED,
    // Logging will succeed.
    INITIALIZED_ENABLED,
    // Logging will be a no-op.
    INITIALIZED_DISABLED,
  }

  private val gate = Any()
  private val LOG = Logger.getLogger(UsageTracker.javaClass.name)
  private var hasAnonymousWriter = false
  private var mode = Mode.UNINITIALIZED
  val initialized
    get() = mode != Mode.UNINITIALIZED

  private lateinit var scheduler: ScheduledExecutorService

  private var exceptionThrown = false
  @JvmStatic var sessionId = UUID.randomUUID().toString()
  @JvmStatic @VisibleForTesting var anonymousWriter: UsageTrackerWriter = NullUsageTracker
  @JvmStatic @VisibleForTesting var loggedInWriter: UsageTrackerWriter = NullUsageTracker
  private var isTesting: Boolean = false
  /**
   * Indicates whether this UsageTracker has a maximum size at which point logs need to be flushed. Zero or less indicates no maximum size
   * at which to flush.
   */
  /*
   * Sets a maximum size at which point logs need to be flushed. Zero or less indicates no
   * flushing until @{link #close()} is called.
   */
  @JvmStatic var maxJournalSize: Int = 0
  /**
   * Indicates whether this UsageTracker has a timeout at which point logs need to be flushed. Zero or less indicates no timeout is set.
   *
   * @return timeout in nanoseconds.
   */
  @JvmStatic
  var maxJournalTime: Long = 0
    private set

  /**
   * The version specified for this UsageTracker. This version when specified is used to populate the product_details.version field of
   * AndroidStudioEvent at time of logging As the version of the product generating the event can be different of the version uploading the
   * event.
   */
  @JvmStatic var version: String? = null
  @JvmStatic
  /** Set when Android Studio is running in development mode. */
  var ideaIsInternal = false
  /** IDE brand specified for this UsageTracker. */
  @JvmStatic var ideBrand: AndroidStudioEvent.IdeBrand = AndroidStudioEvent.IdeBrand.UNKNOWN_IDE_BRAND

  private var job: Job? = null

  /**
   * Gets the global writer to the provided tracker writer so tests can provide their own UsageTrackerWriter implementation. NOTE: Should
   * only be used from Usage Tracker tests.
   */
  @JvmStatic
  val anonymousWriterForTest: UsageTrackerWriter
    @TestOnly
    get() {
      synchronized(gate) {
        return anonymousWriter
      }
    }

  /** Sets a timeout at which point logs need to be flushed. Zero or less indicates no timeout should be used. */
  @JvmStatic
  fun setMaxJournalTime(duration: Long, unit: TimeUnit) {
    runIfUsageTrackerUsable {
      maxJournalTime = unit.toNanos(duration)
      anonymousWriter.scheduleJournalTimeout(maxJournalTime)
      loggedInWriter.scheduleJournalTimeout(maxJournalTime)
    }
  }

  @JvmStatic
  private fun runIfUsageTrackerUsable(callback: () -> Unit) {
    var throwable: Throwable? = null
    synchronized(gate) {
      if (exceptionThrown) {
        return
      }
      ensureInitialized()
      try {
        callback()
      } catch (t: Throwable) {
        exceptionThrown = true
        throwable = t
      }
    }
    if (throwable != null) {
      try {
        LOG.log(Level.SEVERE, throwable) { "UsageTracker call failed" }
      } catch (ignored: Throwable) {}
    }
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent}. */
  @JvmStatic
  fun log(studioEvent: AndroidStudioEvent.Builder) {
    log(AnalyticsSettings.dateProvider.now().time, studioEvent)
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent}. */
  @JvmStatic
  fun log(studioEvent: AndroidStudioEvent) {
    log(studioEvent.toBuilder())
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent} with provided event time. */
  @JvmStatic
  fun log(eventTimeMs: Long, studioEvent: AndroidStudioEvent.Builder) {
    runIfUsageTrackerUsable() {
      anonymousWriter.logAt(eventTimeMs, studioEvent)
      loggedInWriter.logAt(eventTimeMs, studioEvent)
    }
  }

  private fun ensureInitialized() {
    if (!initialized && java.lang.Boolean.getBoolean("idea.is.internal")) {
      // Android Studio Developers: If you hit this exception, you're trying to log metrics before
      // our metrics system has been initialized. Please reach out to the owners of this code
      // to figure out how best to do your logging instead of sending it into the void.
      throw RuntimeException("call to UsageTracker before initialization")
    }
  }

  /** Initializes a [UsageTrackerWriter] for use throughout this process based on user opt-in and other settings. */
  @JvmStatic
  fun initialize(scheduler: ScheduledExecutorService): UsageTrackerWriter {
    if (isTesting) {
      // @coverage:off
      return anonymousWriter
      // @coverage:on
    }

    modeChanged(Mode.INITIALIZED_ENABLED, scheduler)

    return anonymousWriter
  }

  /** initializes or updates AnalyticsSettings into a disabled state. */
  @JvmStatic
  fun disable() {
    modeChanged(Mode.INITIALIZED_DISABLED)
  }

  @JvmStatic
  fun deinitialize() {
    modeChanged(Mode.UNINITIALIZED)
  }

  /**
   * Sets the global writer to the provided tracker so tests can provide their own UsageTracker implementation. NOTE: Should only be used
   * from tests.
   */
  @TestOnly
  @JvmStatic
  fun setWriterForTest(tracker: UsageTrackerWriter): UsageTrackerWriter {
    synchronized(gate) {
      isTesting = true
      mode = Mode.INITIALIZED_ENABLED
      exceptionThrown = false
      val old = anonymousWriter
      anonymousWriter = tracker
      return old
    }
  }

  /** resets the global writer to the null usage tracker, to clean state in tests. NOTE: Should only be used from tests. */
  @TestOnly
  @JvmStatic
  fun cleanAfterTesting() {
    isTesting = false
    anonymousWriter = NullUsageTracker
    mode = Mode.UNINITIALIZED
    exceptionThrown = false
  }

  @TestOnly
  fun updateState() {
    stateChanged(AnalyticsStateManager.analyticsStateFlow.value)
  }

  private fun stateChanged(state: AnalyticsState) {
    val oldWriters: Writers

    synchronized(gate) { oldWriters = updateWriters((AnalyticsStateManager.analyticsStateFlow.value)) }

    oldWriters.close()
  }

  private fun modeChanged(mode: Mode, scheduler: ScheduledExecutorService? = null) {
    if (mode == Mode.INITIALIZED_ENABLED) {
      require(scheduler != null) { "Initializing the usage tracker requires a scheduler." }
    } else {
      require(scheduler == null) { "A scheduler should only be provided during initialization." }
    }

    val oldWriters: Writers
    var oldJob: Job? = null

    synchronized(gate) {
      oldJob = job
      if (mode == Mode.INITIALIZED_ENABLED) {
        this.scheduler = scheduler!!
        val scope = CoroutineScope(this.scheduler.asCoroutineDispatcher())
        job = AnalyticsStateManager.analyticsStateFlow.onEach { stateChanged(it) }.launchIn(scope)
      } else {
        job = null
      }

      this.mode = mode
      oldWriters = updateWriters((AnalyticsStateManager.analyticsStateFlow.value))
    }

    oldWriters.use { oldJob?.cancel() }
  }

  private fun updateWriters(state: AnalyticsState): Writers {
    if (state.level == AnalyticsLevel.LOGGED_IN) {
      require(state.loggedInUser != null) { "A user is required to enable logged in metrics." }
    }

    val shouldCreate = shouldCreateAnonymousWriter(state.level)

    val oldAnonymousWriter =
      if (shouldCreate && hasAnonymousWriter) {
        // optimization to prevent unnecessary creation
        NullUsageTracker
      } else {
        anonymousWriter
      }

    if (shouldCreate && !hasAnonymousWriter) {
      anonymousWriter = createAnonymousWriter()
    } else if (!shouldCreate) {
      anonymousWriter = NullUsageTracker
    }

    hasAnonymousWriter = shouldCreate

    val oldLoggedInWriter = loggedInWriter
    val user = state.loggedInUser
    loggedInWriter =
      if (user != null && shouldCreateLoggedWriter(state.level)) {
        createLoggedInWriter(user)
      } else {
        NullUsageTracker
      }

    return Writers(oldAnonymousWriter, oldLoggedInWriter)
  }

  private fun shouldCreateAnonymousWriter(level: AnalyticsLevel): Boolean {
    return mode == Mode.INITIALIZED_ENABLED && level != AnalyticsLevel.NONE
  }

  private fun shouldCreateLoggedWriter(level: AnalyticsLevel): Boolean {
    return mode == Mode.INITIALIZED_ENABLED && level == AnalyticsLevel.LOGGED_IN
  }

  private fun createAnonymousWriter(): UsageTrackerWriter {
    return AnonymousUsageTrackerWriter(scheduler, Paths.get(AnalyticsPaths.spoolDirectory))
  }

  private fun createLoggedInWriter(user: LoggedInUser): UsageTrackerWriter {
    val spoolLocationId = Hashing.farmHashFingerprint64().hashUnencodedChars(user.emailAddress.lowercase()).toString()
    val path = Paths.get(AnalyticsPaths.spoolDirectory, spoolLocationId)
    return LoggedInUsageTrackerWriter(scheduler, path)
  }

  var listener: (event: AndroidStudioEvent.Builder) -> Unit = {}
}
