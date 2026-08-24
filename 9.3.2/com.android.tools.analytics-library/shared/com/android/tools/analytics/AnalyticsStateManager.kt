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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Represents the different levels of analytics data collection. */
enum class AnalyticsLevel {
  /** No data is collected. */
  NONE,
  /** Anonymous data is collected. */
  ANONYMOUS,
  /** Both anonymous data and data associated with a logged-in user is collected. */
  LOGGED_IN,
}

/**
 * Represents a user who is logged in.
 *
 * @property emailAddress The email address of the logged-in user.
 * @property callback A function to call to retrieve an authentication token or similar credential.
 */
data class LoggedInUser(val emailAddress: String, val callback: (() -> String?))

/**
 * Represents the current state of analytics settings.
 *
 * @property level The current [AnalyticsLevel].
 * @property loggedInUser The [LoggedInUser] if a user is logged in, otherwise null.
 */
data class AnalyticsState(val level: AnalyticsLevel, val loggedInUser: LoggedInUser?)

/**
 * Manages the state of analytics settings and user consent.
 *
 * This object provides properties to control data sharing, email consent, and the logged-in user. It exposes the current [AnalyticsState]
 * through a [MutableStateFlow].
 */
object AnalyticsStateManager {
  private val gate = Any()

  // Indicates the user has opted in for metrics collection.
  private var _dataSharing: Boolean = false
  // Indicates the user has consented to receive emails
  private var _emailConsent: Boolean = false
  // Contains the currently logged-in user, if any
  private var _loggedInUser: LoggedInUser? = null

  private val _analyticsStateFlow = MutableStateFlow(AnalyticsState(AnalyticsLevel.NONE, null))
  val analyticsStateFlow: StateFlow<AnalyticsState> = _analyticsStateFlow

  private fun updateState() {
    val level =
      when {
        !dataSharing -> AnalyticsLevel.NONE
        _loggedInUser == null || !emailConsent -> AnalyticsLevel.ANONYMOUS
        else -> AnalyticsLevel.LOGGED_IN
      }
    _analyticsStateFlow.value = AnalyticsState(level, _loggedInUser)
  }

  @JvmStatic
  var dataSharing: Boolean
    get() = _dataSharing
    set(value) {
      synchronized(gate) {
        if (_dataSharing == value) {
          return
        }
        _dataSharing = value
        updateState()
      }
    }

  var emailConsent: Boolean
    get() = _emailConsent
    set(value) {
      synchronized(gate) {
        if (_emailConsent == value) {
          return
        }
        _emailConsent = value
        updateState()
      }
    }

  var loggedInUser: LoggedInUser?
    get() = _loggedInUser
    set(value) {
      synchronized(gate) {
        _loggedInUser = value
        updateState()
      }
    }
}
