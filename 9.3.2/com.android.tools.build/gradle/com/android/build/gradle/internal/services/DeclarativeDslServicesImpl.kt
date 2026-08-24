/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.utils.GradleEnvironmentProvider
import com.android.build.gradle.internal.utils.GradleEnvironmentProviderImpl
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.builder.model.v2.ide.ProjectType
import java.io.File
import java.lang.reflect.Proxy
import org.gradle.api.DomainObjectSet
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceRegistry

open class DeclarativeBaseServicesImpl(private val objectFactory: ObjectFactory, providers: ProviderFactory) : BaseServices {
  final override fun <T : Any> newInstance(type: Class<T>, vararg args: Any?): T = objectFactory.newInstance(type, *args)

  final override fun file(file: Any): File = throw UnsupportedOperationException("Not supported")

  final override val issueReporter: IssueReporter =
    object : IssueReporter() {
      override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) {
        // do nothing
      }

      override fun hasIssue(type: Type): Boolean = false
    }

  // returning fake deprecation reporter
  final override val deprecationReporter: DeprecationReporter =
    object : DeprecationReporter {
      override fun reportDeprecatedUsage(
        newDslElement: String,
        oldDslElement: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
      ) {}

      override fun reportDeprecatedApi(
        newApiElement: String?,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
        requiresOptIn: Boolean,
      ) {}

      override fun reportRemovedApi(oldApiElement: String, url: String, deprecationTarget: DeprecationReporter.DeprecationTarget) {}

      override fun reportDeprecatedValue(
        dslElement: String,
        oldValue: String,
        newValue: String?,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
      ) {}

      override fun reportObsoleteUsage(oldDslElement: String, deprecationTarget: DeprecationReporter.DeprecationTarget) {}

      override fun reportRenamedConfiguration(
        newConfiguration: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
      ) {}

      override fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationReporter.DeprecationTarget,
      ) {}

      override fun reportOptionIssuesIfAny(option: Option<*>, value: Any) {}
    }

  final override val projectOptions: ProjectOptions = ProjectOptions(providers)

  final override val buildServiceRegistry: BuildServiceRegistry
    get() = throw UnsupportedOperationException("Not supported")

  final override val gradleEnvironmentProvider: GradleEnvironmentProvider = GradleEnvironmentProviderImpl(providers)

  final override val projectInfo: ProjectInfo
    get() = throw UnsupportedOperationException("Not supported")

  final override val builtInKotlinServices: BuiltInKotlinServices
    get() = throw UnsupportedOperationException("Not supported")
}

class DeclarativeDslServicesImpl
constructor(
  private val objectFactory: ObjectFactory,
  providers: ProviderFactory,
  private val layout: ProjectLayout,
  override val projectType: ProjectType?,
) : DeclarativeBaseServicesImpl(objectFactory, providers), DslServices {

  @Deprecated("Should not be used in new DSL object. Only for older DSL objects.")
  override val sdkComponents: Provider<SdkComponentsBuildService>
    get() = throw UnsupportedOperationException("Not supported")

  @Deprecated("Should not be used in new DSL object. Only for older DSL objects.")
  override val versionedSdkLoaderService: VersionedSdkLoaderService
    get() = throw UnsupportedOperationException("Not supported")

  override fun <T : Any> domainObjectSet(type: Class<T>): DomainObjectSet<T> = objectFactory.domainObjectSet(type)

  override fun <T : Any> domainObjectContainer(type: Class<T>, factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T> =
    objectFactory.domainObjectContainer(type, factory)

  override fun <T : Any> domainObjectContainer(type: Class<T>): NamedDomainObjectContainer<T> = objectFactory.domainObjectContainer(type)

  override fun <T : Any> polymorphicDomainObjectContainer(type: Class<T>): ExtensiblePolymorphicDomainObjectContainer<T> =
    objectFactory.polymorphicDomainObjectContainer(type)

  override fun <T : Any> property(type: Class<T>): Property<T> = objectFactory.property(type)

  override fun directoryProperty(): DirectoryProperty = objectFactory.directoryProperty()

  override fun <T : Any> provider(type: Class<T>, value: T?): Provider<T> = objectFactory.property(type).also { it.set(value) }

  // requires only for proguardFile that we don't support for declarative - using sourceSet
  override val buildDirectory: DirectoryProperty
    get() = layout.buildDirectory

  override val logger: Logger
    get() =
      Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, _ ->
        when (method.returnType) {
          Boolean::class.javaPrimitiveType -> false
          String::class.java -> "NoOpLogger"
          else -> null
        }
      } as Logger

  // don't need this for DSL instantiation
  override val configurations: ConfigurationContainer
    get() = throw UnsupportedOperationException("Not supported")

  override fun <T : Any> newDecoratedInstance(dslClass: Class<T>, vararg args: Any): T {
    return newInstance(androidPluginDslDecorator.decorate(dslClass), *args)
  }
}
