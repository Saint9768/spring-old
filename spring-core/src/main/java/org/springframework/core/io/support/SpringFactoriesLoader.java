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

package org.springframework.core.io.support;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Spring框架内部使用的通用工厂加载机制。
 *
 * SpringFactoriesLoader.loadFactories(...)从“META-INF/spring.factories”文件加载和实例化给定类型的工厂，这些文件可能存在于类路径中
 * 的多个JAR文件中。spring.factories文件必须是Properties格式，其中key是接口或抽象类的完全限定名称，value是逗号分隔的实现类名称列表。
 * 例如：
 *    example.MyService=example.MyServiceImpl1,example.MyServiceImpl2
 * 其中example.MyService是接口名称，MyServiceImpl1和MyServiceImpl2是两个实现。
 */
public final class SpringFactoriesLoader {

	/**
	 * The location to look for factories.
	 * <p>Can be present in multiple JAR files.
	 */
	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";


	private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);


	static final Map<ClassLoader, Map<String, List<String>>> cache = new ConcurrentReferenceHashMap<>();


	private SpringFactoriesLoader() {
	}

	/**
	 * 使用给定的类加载器从“META-INF/spring.factories”加载并实例化给定类型的工厂实现。
	 * 返回的工厂通过 AnnotationAwareOrderComparator 进行排序。
	 * 如果需要自定义实例化策略，请使用 loadFactoryNames(java.lang.Class<?>, java.lang.ClassLoader) 获取所有已注册的工厂名称。
	 * 从 Spring Framework 5.3 开始，如果为给定的工厂类型发现重复的实现类名称，则只会实例化重复的实现类型的一个实例。
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
		Assert.notNull(factoryType, "'factoryType' must not be null");
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
		List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded [" + factoryType.getName() + "] names: " + factoryImplementationNames);
		}
		List<T> result = new ArrayList<>(factoryImplementationNames.size());
		for (String factoryImplementationName : factoryImplementationNames) {
			result.add(instantiateFactory(factoryImplementationName, factoryType, classLoaderToUse));
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

	/**
	 * 查找指定入参的factoryType所配置的类名集合，即：是否在所有的jar包依赖的META-INF/spring.factories的配置文件中有配置的内容。
	 * 如果有，则返回spring.factories文件中配置的对应等号"="右边的的类名列表；
	 * 如果没有，则返回Collections.emptyList()
	 */
	// eg1: factoryType=Bootstrapper.class  classLoader=ClassLoaders$AppClassLoader@1967
	public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
		/** 首先：获得类加载器 */
		ClassLoader classLoaderToUse = classLoader;
		// eg1: classLoaderToUse=ClassLoaders$AppClassLoader@1967
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}

		/** 其次：获得factoryType的全路径名 */
		// eg1: factoryTypeName="org.springframework.boot.Bootstrapper"
		String factoryTypeName = factoryType.getName();


		/**
		 * 最后：
		 * 遍历所有jar包下【META-INF/spring.factories】路径文件，将配置文件信息拼装为Map<String, List<String>>数据格式进行返回。
		 * 然后，去获取是否有key等于factoryTypeName的配置。如果有，那么从map中获取对应的value值。如果没有，则返回Collections.emptyList()
		 *
		 * MAP.getOrDefault(Object key, V defaultValue):
		 * * 如果能从map中获得key对应的value值，则返回该value；
		 * * 如果map中不存在指定的key值时，则返回defaultValue；
		 */
		// eg1: classLoaderToUse=ClassLoaders$AppClassLoader@1967  factoryTypeName="org.springframework.boot.Bootstrapper"
		return loadSpringFactories(classLoaderToUse).getOrDefault(factoryTypeName, Collections.emptyList()); // eg1：返回值为Collections.emptyList()
	}


	// eg1: classLoader=ClassLoaders$AppClassLoader@1967
	/**
	 * 解析所有加载的jar包中具有META-INF/spring.factories配置文件的配置内容，并组装为Map<String, List<String>>数据结构，方法返回。
	 *
	 * 处理流程：
	 * 1> 读取所有依赖jar包中，每一个存在META-INF/spring.factories的配置文件内容;
	 * 2> 将配置文件中的内容维护到Map<String, List<String>> result中，如果key相同，则value取合集;
	 * 3> 将result维护到缓冲cache中——key=ClassLoader value=result
	 * 4> 将result作为返回值返回。
	 */
	private static Map<String, List<String>> loadSpringFactories(ClassLoader classLoader) {
		/** 首先：去缓存中，查询是否有入参classLoader对应的配置信息，如果存在，则表明服务之前解析过配置文件。如果不存在，则进行解析操作 */
		// eg1: cache.size()=0
		Map<String, List<String>> result = cache.get(classLoader);
		// eg1: result=null
		if (result != null) {
			return result;
		}

		result = new HashMap<>();
		try {
			/** 其次：获得所有依赖jar包中，具有META-INF/spring.factories配置文件的jar文件URI，并依次进行遍历 */
			// eg1: FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
			Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
			while (urls.hasMoreElements()) {
				// eg1-1: jar:file:/Users/muse/.m2/repository/org/springframework/boot/spring-boot/2.5.1/spring-boot-2.5.1.jar!/META-INF/spring.factories
				// eg1-2: jar:file:/Users/muse/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/2.5.1/spring-boot-autoconfigure-2.5.1.jar!/META-INF/spring.factories
				// eg1-3: jar:file:/Users/muse/.m2/repository/org/springframework/spring-beans/5.3.8/spring-beans-5.3.8.jar!/META-INF/spring.factories
				URL url = urls.nextElement();

				// eg1-1: 通过url获得资源resource，并将spring-boot-2.5.1.jar包下spring.factories文件中的配置信息，加载到properties中。共计13个配置项
				// eg1-2: 通过url获得资源resource，并将spring-boot-autoconfigure-2.5.1.jar包下spring.factories文件中的配置信息，加载到properties中。共计9个配置项
				// eg1-3: 通过url获得资源resource，并将spring-beans-5.3.8.jar包下spring.factories文件中的配置信息，加载到properties中。共计1个配置项
				UrlResource resource = new UrlResource(url);
				/** 第三: 将spring.factories配置的内容转化成properties实例 */
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);

				/** 最后，遍历properties实例，将key和value维护到Map<String, List<String>> result数据结构中 */
				// eg1-1:【仅以第一次循环为例】
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					// eg1-1-1: factoryTypeName="org.springframework.boot.diagnostics.FailureAnalysisReporter"
					String factoryTypeName = ((String) entry.getKey()).trim();

					// eg1-1-1: factoryImplementationNames=String[]{"org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter"}
					String[] factoryImplementationNames = StringUtils.commaDelimitedListToStringArray((String) entry.getValue());
					for (String factoryImplementationName : factoryImplementationNames) {

						// eg1: result key="org.springframework.boot.diagnostics.FailureAnalysisReporter"
						//             value={"org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter"}
						/**
						 * result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>()):
						 * * 如果<Map>result中不存在指定的key=factoryTypeName，则创建并将value赋值为new ArrayList<>()，再将value值返回。
						 * * 如果存在指定的key，则直接将value作为返回值。
						 */
						result.computeIfAbsent(factoryTypeName, key -> new ArrayList<>()).add(factoryImplementationName.trim());
					}
				}
			}

			/**
			 * Replace all lists with unmodifiable lists containing unique elements
			 * 把result结果中的value值中集合去重，并且包装为不可修改的list
			 *
			 * collectingAndThen(Collector<T,A,R> downstream, Function<R,RR> finisher)方法的使用:
			 * * 先进行结果集的收集，然后将收集到的结果集进行下一步的处理；即：就是把第一个参数downstream的结果，交给第二个参数Function
			 * * 函数的参数里面，R apply(T t)，也就是将结果设置成t。
			 */
			// eg1: 将value中的ArrayList类型，由UnmodifiableRandomAccessList封装一层。
			result.replaceAll((factoryType, implementations) -> implementations.stream().distinct()
					.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

			/**
			 * 为什么result.size()的最终大小为18个配置项？
			 * ---------------------------------------------------------------------------------------------
			 * 【spring-boot-2.5.1.jar】 配置项key：共13个
			 * org.springframework.boot.logging.LoggingSystemFactory
			 * org.springframework.boot.env.PropertySourceLoader
			 * org.springframework.boot.context.config.ConfigDataLocationResolver
			 * org.springframework.boot.context.config.ConfigDataLoader
			 * org.springframework.boot.SpringApplicationRunListener
			 * org.springframework.boot.SpringBootExceptionReporter
			 * org.springframework.context.ApplicationContextInitializer
			 * org.springframework.context.ApplicationListener
			 * org.springframework.boot.env.EnvironmentPostProcessor
			 * org.springframework.boot.diagnostics.FailureAnalyzer
			 * org.springframework.boot.diagnostics.FailureAnalysisReporter
			 * org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector
			 * org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector
			 *---------------------------------------------------------------------------------------------
			 * 【spring-boot-autoconfigure-2.5.1.jar】 配置项key：共9个，重复5个
			 * （重复）org.springframework.context.ApplicationContextInitializer
			 * （重复）org.springframework.context.ApplicationListener
			 * （重复）org.springframework.boot.env.EnvironmentPostProcessor
			 * org.springframework.boot.autoconfigure.AutoConfigurationImportListener
			 * org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
			 * org.springframework.boot.autoconfigure.EnableAutoConfiguration
			 * （重复）org.springframework.boot.diagnostics.FailureAnalyzer
			 * org.springframework.boot.autoconfigure.template.TemplateAvailabilityProvider
			 * （重复）org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector
			 *---------------------------------------------------------------------------------------------
			 * 【spring-beans-5.3.8.jar】 配置项key：共1个
			 * org.springframework.beans.BeanInfoFactory
			 *
			 * 【总计配置项key】13 + 4 + 1 = 18个
			 */
			// eg1: classLoader=ClassLoaders$AppClassLoader@1967   result.size()=18
			cache.put(classLoader, result);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}

		// eg1: result.size()=18
		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T> T instantiateFactory(String factoryImplementationName, Class<T> factoryType, ClassLoader classLoader) {
		try {
			Class<?> factoryImplementationClass = ClassUtils.forName(factoryImplementationName, classLoader);
			if (!factoryType.isAssignableFrom(factoryImplementationClass)) {
				throw new IllegalArgumentException(
						"Class [" + factoryImplementationName + "] is not assignable to factory type [" + factoryType.getName() + "]");
			}
			return (T) ReflectionUtils.accessibleConstructor(factoryImplementationClass).newInstance();
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException(
				"Unable to instantiate factory class [" + factoryImplementationName + "] for factory type [" + factoryType.getName() + "]",
				ex);
		}
	}

}
