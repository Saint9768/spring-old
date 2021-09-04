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

import java.util.Map;

/**
 * 大多数环境（Environment）类型要实现的配置接口。
 * 提供用于设置活动和默认配置文件以及操作基础属性源的工具。
 * 允许客户端通过ConfigurablePropertyResolver超级接口（superinterface）设置和验证所需的属性、自定义转换服务等。
 *
 * 操作属性源
 * 可以删除、重新排序或替换属性源；并且可以使用从getPropertySources()返回的MutablePropertySources实例添加其他属性源。
 * 以下示例针对ConfigurableEnvironment的StandardEnvironment实现，但通常适用于任何实现，尽管特定的默认属性源可能不同。
 *
 * 示例：添加具有最高搜索优先级的新属性源
 *     ConfigurableEnvironment environment = new StandardEnvironment();
 *     MutablePropertySources propertySources = environment.getPropertySources();
 *     Map<String, String> myMap = new HashMap<>();
 *     myMap.put("xyz", "myValue");
 *     propertySources.addFirst(new MapPropertySource("MY_MAP", myMap));
 *
 * 示例：删除默认的系统属性属性源
 *     MutablePropertySources propertySources = environment.getPropertySources();
 *     propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
 *
 * 示例：模拟系统环境以进行测试
 *     MutablePropertySources propertySources = environment.getPropertySources();
 *     MockPropertySource mockEnvVars = new MockPropertySource().withProperty("xyz", "myValue");
 *     propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mockEnvVars);
 *
 * 当ApplicationContext正在使用Environment时，重要的是在调用上下文的refresh()方法之前执行任何此类PropertySource操作。
 * 这确保在容器引导过程中所有属性源都可用，包括属性占位符配置器的使用。
 */
public interface ConfigurableEnvironment extends Environment, ConfigurablePropertyResolver {

	/**
	 * 指定此环境的活动配置文件集。
	 * 在容器引导期间评估配置文件以确定是否应向容器注册bean定义。
	 * 任何现有的活动配置文件都将替换为给定的参数；使用零参数调用以清除当前的活动配置文件集。
	 * 使用addActiveProfile(java.lang.String) 添加配置文件，同时保留现有集。
	 */
	void setActiveProfiles(String... profiles);

	/**
	 * 将一个配置文件添加到当前的活动配置文件集
	 */
	void addActiveProfile(String profile);

	/**
	 * 如果没有其他配置文件通过setActiveProfiles(java.lang.String...) 显式激活，则指定默认激活的配置文件集。
	 */
	void setDefaultProfiles(String... profiles);

	/**
	 * 以可变形式返回此Environment的PropertySources，允许在针对此Environment对象解析属性时操作应搜索的PropertySource对象集。
	 * 各种MutablePropertySources方法（例如：addFirst、addLast、addBefore 和 addAfter）允许对属性源排序进行细粒度控制。
	 * 例如，这在确保某些用户定义的属性源比默认属性源（例如系统属性集或系统环境变量集）具有搜索优先级时很有用。
	 */
	MutablePropertySources getPropertySources();

	/**
	 * 如果当前SecurityManager允许，则返回System.getProperties()的值。
	 * 否则返回一个映射实现，该实现将尝试使用对System.getProperty(String) 的调用访问各个键。
	 * 请注意，大多数环境实现将包含此系统属性映射作为要搜索的默认PropertySource。 因此，建议不要直接使用此方法，除非明确打算绕过其他属性源。
	 *
	 * 在返回的Map上调用Map.get(Object) 永远不会抛出IllegalAccessException；
	 * 在SecurityManager禁止访问某个属性的情况下，将返回null并发出一条INFO级别的日志消息，指出异常。
	 */
	Map<String, Object> getSystemProperties();

	/**
	 * 如果当前SecurityManager允许，则返回System.getenv()的值。
	 * 否则返回一个映射实现，该实现将尝试使用对System.getenv(String) 的调用访问各个键。
	 * 请注意，大多数Environment实现将包含此系统环境映射作为要搜索的默认PropertySource。 因此，建议不要直接使用此方法，除非明确打算绕过其他属性源。
	 *
	 * 在返回的Map上调用Map.get(Object) 永远不会抛出IllegalAccessException；
	 * 在SecurityManager禁止访问某个属性的情况下，将返回null并发出一条INFO级别的日志消息，指出异常。
	 */
	Map<String, Object> getSystemEnvironment();

	/**
	 * 将给定的父环境的活动配置文件、默认配置文件和属性源附加到此（子）环境各自的集合中。
	 * 对于存在于父和子中的任何同名PropertySource实例，将保留子实例并丢弃父实例。
	 * 这具有允许子项覆盖属性源以及避免通过常见属性源类型进行冗余搜索的效果，例如：系统环境和系统属性。
	 *
	 * 活动和默认配置文件名称也会过滤重复，以避免混淆和冗余存储。
	 *
	 * 在任何情况下，父环境都保持不变。请注意，在调用合并之后发生的对父环境的任何更改都不会反映在子环境中。因此，在调用合并之前，应注意配置
	 * 父属性源和配置文件信息。
	 */
	void merge(ConfigurableEnvironment parent);

}
