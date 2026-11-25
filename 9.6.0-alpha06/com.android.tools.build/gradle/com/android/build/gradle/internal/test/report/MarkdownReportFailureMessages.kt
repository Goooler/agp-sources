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

@file:JvmName("MarkdownReportFailureMessages")

package com.android.build.gradle.internal.test.report

import java.io.File
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.tasks.VerificationException
import org.gradle.internal.logging.ConsoleRenderer

/**
 * Returns the line that test tasks add to their "There were failing tests" message to point to the Markdown test report.
 *
 * The failure message is the last thing a build prints, so it's the part of the output that coding agents are most likely to read. It
 * otherwise only points to the HTML report.
 */
fun markdownReportFailureLine(markdownReport: File): String = "Markdown version: ${ConsoleRenderer().asClickableFileUrl(markdownReport)}"

/**
 * Returns [failure], thrown by Gradle's `Test` task when tests fail, with [markdownReportFailureLine] added to its message. Returns
 * [failure] unchanged if there's no [markdownReport] or if it isn't a failing tests message.
 *
 * The new exception keeps the type Gradle uses for failing tests, so the build reports it the same way.
 */
fun withMarkdownReportLine(failure: VerificationException, markdownReport: File?): VerificationException {
  val message = failure.message
  if (markdownReport == null || message == null || !message.startsWith(FAILING_TESTS_MESSAGE_PREFIX)) return failure
  val newMessage = "$message\n${markdownReportFailureLine(markdownReport)}"
  return if (failure is MarkedVerificationException) MarkedVerificationException(newMessage) else VerificationException(newMessage)
}

/** Start of the message of Gradle's `Test` task, and of AGP's test tasks, when tests fail. */
private const val FAILING_TESTS_MESSAGE_PREFIX = "There were failing tests."
