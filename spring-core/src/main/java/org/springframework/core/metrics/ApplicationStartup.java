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

/**
 * 使用StartupStep检测应用程序启动阶段。
 * 核心容器及其基础设施组件可以使用 ApplicationStartup 来标记应用程序启动期间的步骤，并收集有关执行上下文或其处理时间的数据指标。
 *
 * @author Brian Clozel
 * @since 5.3
 */
public interface ApplicationStartup {

	/**
	 * Default "no op" {@code ApplicationStartup} implementation.
	 * <p>This variant is designed for minimal overhead and does not record data.
	 */
	ApplicationStartup DEFAULT = new DefaultApplicationStartup();

	/**
	 * 创建一个新步骤并标记它的开始。
	 * 步骤名称描述当前操作或阶段。 此技术名称应为“.”命名空间，可以被重用，来描述其他实例在应用程序启动期间的相同步骤。
	 *
	 * @param name the step name
	 */
	StartupStep start(String name);

}
