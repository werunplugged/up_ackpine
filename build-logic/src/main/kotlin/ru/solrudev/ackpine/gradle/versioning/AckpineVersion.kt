/*
 * Copyright (C) 2024 Ilya Fomichev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.solrudev.ackpine.gradle.versioning

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.registerIfAbsent

private const val QUALIFIER_PROPERTY = "ackpine.version.qualifier"
private const val DEFAULT_QUALIFIER = "unplugged.local"

/**
 * Returns a provider of a [Version] object parsed from `version.json` file in root project directory.
 *
 * The version carries the fork's [qualifier][Version.qualifier] from the `ackpine.version.qualifier` Gradle property,
 * set in `gradle.properties` and increased by hand for every release (`unplugged.1`, `unplugged.2`, ...). It falls back
 * to `unplugged.local` if the property is missing; an empty value leaves the upstream version.
 */
public val Project.ackpineVersion: Provider<Version>
	get() = gradle
		.sharedServices
		.registerIfAbsent("versioning", VersioningService::class) {
			parameters.versionFile = layout.settingsDirectory.file("version.json")
			parameters.qualifier = providers.gradleProperty(QUALIFIER_PROPERTY).orElse(DEFAULT_QUALIFIER)
		}
		.map { service ->
			service.version
		}