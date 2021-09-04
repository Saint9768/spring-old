/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.metrics;

import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * Step记录有关 {@link ApplicationStartup} 期间发生的特定阶段或操作的指标。
 *
 * {@code StartupStep} 的生命周期如下所示:
 * 该步骤是通过调用 {@link ApplicationStartup#start(String) the application startup} 来创建和启动的，并被分配一个唯一的 {@link StartupStep#getId() id}。
 * 然后我们可以在处理过程中使用 {@link Tags} 附加信息
 * 然后我们需要标记步骤的 {@link #end()}
 * 它的实现类，可以跟踪“执行时间”或步骤的其他指标。
 *
 * @author Brian Clozel
 * @since 5.3
 */
public interface StartupStep {

	/**
	 * 返回启动步骤的名称
	 * 步骤名称描述当前操作或阶段。
	 * 此技术名称应为“.”命名空间，可以重用来描述应用程序启动期间类似步骤的其他实例。
	 */
	String getName();

	/**
	 * 在应用程序启动中返回此步骤的唯一ID
	 */
	long getId();

	/**
	 * Return, if available, the id of the parent step.
	 * <p>The parent step is the step that was started the most recently
	 * when the current step was created.
	 */
	@Nullable
	Long getParentId();

	/**
	 * Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value tag value
	 */
	StartupStep tag(String key, String value);

	/**
	 * Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value {@link Supplier} for the tag value
	 */
	StartupStep tag(String key, Supplier<String> value);

	/**
	 * Return the {@link Tag} collection for this step.
	 */
	Tags getTags();

	/**
	 * Record the state of the step and possibly other metrics like execution time.
	 * <p>Once ended, changes on the step state are not allowed.
	 */
	void end();


	/**
	 * Immutable collection of {@link Tag}.
	 */
	interface Tags extends Iterable<Tag> {
	}


	/**
	 * Simple key/value association for storing step metadata.
	 */
	interface Tag {

		/**
		 * Return the {@code Tag} name.
		 */
		String getKey();

		/**
		 * Return the {@code Tag} value.
		 */
		String getValue();
	}

}
