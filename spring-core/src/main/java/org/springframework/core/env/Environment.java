/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.env;

/**
 * 表示当前应用程序运行环境的接口。
 * 对应用程序环境的两个关键方面进行建模：配置文件和属性。
 * 与属性访问相关的方法通过PropertyResolver超接口（superinterface）公开。
 *
 * 配置文件是一个命名的、逻辑​​的bean定义组，仅当给定的配置文件处于活动状态时才向容器注册。
 * Bean可以分配给配置文件，无论是在XML中定义还是通过注释；有关语法详细信息，请参阅spring-beans 3.1模式或@Profile注释。
 * 与配置文件相关的环境对象的作用是确定哪些配置文件（如果有）当前是活动的，以及默认情况下哪些配置文件（如果有）应该是活动的。
 *
 * 属性在几乎所有应用程序中都扮演着重要的角色，并且可能源自各种来源：属性文件、JVM系统属性、系统环境变量、JNDI、servlet上下文参数、
 * ad-hoc属性对象、映射等。
 * 与属性相关的环境对象的作用是为用户提供方便的服务接口，用于配置属性源并从中解析属性。
 *
 * 在ApplicationContext中管理的Bean可以注册为EnvironmentAware或@Inject环境，以便直接查询配置文件状态或解析属性。
 *
 * 然而，在大多数情况下，应用程序级bean不需要直接与环境交互，而是可能必须将 ${...} 属性值替换为属性占位符配置器，
 * 例如：PropertySourcesPlaceholderConfigurer，它本身是EnvironmentAware并且截至使用<context:property-placeholder/>时默认注册Spring 3.1。
 *
 * 环境对象的配置必须通过ConfigurableEnvironment接口完成，从所有AbstractApplicationContext子类getEnvironment()方法返回。
 * 请参阅ConfigurableEnvironment Javadoc以获取演示在application context refresh()之前操作属性源的用法示例。
 */
public interface Environment extends PropertyResolver {

	/**
	 * 返回为此环境明确激活的配置文件集。
	 * 配置文件用于创建有条件注册的bean定义的逻辑分组，例如：基于部署环境。
	 * 可以通过将“spring.profiles.active”设置为系统属性 或 通过调用ConfigurableEnvironment.setActiveProfiles(String...) 来激活配置文件。
	 * 如果没有明确指定激活的配置文件，则将自动激活任何默认配置文件。
	 */
	String[] getActiveProfiles();

	/**
	 * 当没有明确设置活动配置文件时，默认情况下返回要激活的配置文件集。
	 */
	String[] getDefaultProfiles();

	/**
	 * 返回一个或多个给定的配置文件是否处于活动状态，或者在没有明确的活动配置文件的情况下，一个或多个给定的配置文件是否包含在一组默认配置文件中。
	 * 如果配置文件以 '!' 开头逻辑被反转，即：如果给定的配置文件未激活，该方法将返回 true。
	 * 例如，env.acceptsProfiles("p1", "!p2") 如果配置文件'p1'处于活动状态或'p2'未处于活动状态，则将返回 true。
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);

	/**
	 * 返回活动配置文件是否与给定的配置文件谓词匹配。
	 */
	boolean acceptsProfiles(Profiles profiles);

}
