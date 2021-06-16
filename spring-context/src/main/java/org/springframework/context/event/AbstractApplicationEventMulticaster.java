/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * Abstract implementation of the {@link ApplicationEventMulticaster} interface,
 * providing the basic listener registration facility.
 *
 * <p>Doesn't permit multiple instances of the same listener by default,
 * as it keeps listeners in a linked Set. The collection class used to hold
 * ApplicationListener objects can be overridden through the "collectionClass"
 * bean property.
 *
 * <p>Implementing ApplicationEventMulticaster's actual {@link #multicastEvent} method
 * is left to subclasses. {@link SimpleApplicationEventMulticaster} simply multicasts
 * all events to all registered listeners, invoking them in the calling thread.
 * Alternative implementations could be more sophisticated in those respects.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 1.2.3
 * @see #getApplicationListeners(ApplicationEvent, ResolvableType)
 * @see SimpleApplicationEventMulticaster
 */
public abstract class AbstractApplicationEventMulticaster
		implements ApplicationEventMulticaster, BeanClassLoaderAware, BeanFactoryAware {

	private final DefaultListenerRetriever defaultRetriever = new DefaultListenerRetriever();

	final Map<ListenerCacheKey, CachedListenerRetriever> retrieverCache = new ConcurrentHashMap<>(64);

	@Nullable
	private ClassLoader beanClassLoader;

	@Nullable
	private ConfigurableBeanFactory beanFactory;


	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		if (this.beanClassLoader == null) {
			this.beanClassLoader = this.beanFactory.getBeanClassLoader();
		}
	}

	private ConfigurableBeanFactory getBeanFactory() {
		if (this.beanFactory == null) {
			throw new IllegalStateException("ApplicationEventMulticaster cannot retrieve listener beans " +
					"because it is not associated with a BeanFactory");
		}
		return this.beanFactory;
	}

	// eg1: 从所有依赖的jar包中的spring.factories文件中解析出来，一共8个ApplicationListener的实现类，调用该方法8次
	// application.getListeners()={
	//     EnvironmentPostProcessorApplicationListener@2154, AnsiOutputApplicationListener@2155,
	//     LoggingApplicationListener@2156, BackgroundPreinitializer@2157, DelegatingApplicationListener@2158,
	//     ParentContextCloserApplicationListener@2159, ClearCachesApplicationListener@2160, FileEncodingApplicationListener@2161
	//  }
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		synchronized (this.defaultRetriever) {
			Object singletonTarget = AopProxyUtils.getSingletonTarget(listener);
			if (singletonTarget instanceof ApplicationListener) {
				this.defaultRetriever.applicationListeners.remove(singletonTarget);
			}
			// eg1: 将8个ApplicationListener的实现类保存到applicationListeners中
			this.defaultRetriever.applicationListeners.add(listener);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void addApplicationListenerBean(String listenerBeanName) {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListenerBeans.add(listenerBeanName);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListener(ApplicationListener<?> listener) {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListeners.remove(listener);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListenerBean(String listenerBeanName) {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListenerBeans.remove(listenerBeanName);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate) {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListeners.removeIf(predicate);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeApplicationListenerBeans(Predicate<String> predicate) {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListenerBeans.removeIf(predicate);
			this.retrieverCache.clear();
		}
	}

	@Override
	public void removeAllListeners() {
		synchronized (this.defaultRetriever) {
			this.defaultRetriever.applicationListeners.clear();
			this.defaultRetriever.applicationListenerBeans.clear();
			this.retrieverCache.clear();
		}
	}


	/**
	 * Return a Collection containing all ApplicationListeners.
	 * @return a Collection of ApplicationListeners
	 * @see org.springframework.context.ApplicationListener
	 */
	protected Collection<ApplicationListener<?>> getApplicationListeners() {
		synchronized (this.defaultRetriever) {
			return this.defaultRetriever.getApplicationListeners();
		}
	}

	/**
	 * Return a Collection of ApplicationListeners matching the given
	 * event type. Non-matching listeners get excluded early.
	 * @param event the event to be propagated. Allows for excluding
	 * non-matching listeners early, based on cached matching information.
	 * @param eventType the event type
	 * @return a Collection of ApplicationListeners
	 * @see org.springframework.context.ApplicationListener
	 */
	// eg1: event=ApplicationStartingEvent@47d90b9e  eventType=ResolvableType@2566
	protected Collection<ApplicationListener<?>> getApplicationListeners(ApplicationEvent event, ResolvableType eventType) {

		// eg1: source=SpringApplication@2116
		Object source = event.getSource();

		// eg1: sourceType=org.springframework.boot.SpringApplication.class
		Class<?> sourceType = (source != null ? source.getClass() : null);

		// eg1: eventType=ResolvableType@2566  sourceType=org.springframework.boot.SpringApplication.class
		ListenerCacheKey cacheKey = new ListenerCacheKey(eventType, sourceType);

		/** 要填充的潜在新检索器 */
		CachedListenerRetriever newRetriever = null;

		/** Quick check for existing entry on ConcurrentHashMap */
		CachedListenerRetriever existingRetriever = this.retrieverCache.get(cacheKey);

		// eg1: existingRetriever=null
		if (existingRetriever == null) {
			if (this.beanClassLoader == null || // eg1: beanClassLoader=null 满足条件，直接进入if代码块中
					(ClassUtils.isCacheSafe(event.getClass(), this.beanClassLoader) &&
							 (sourceType == null || ClassUtils.isCacheSafe(sourceType, this.beanClassLoader)))) {

				newRetriever = new CachedListenerRetriever();
				/** 维护到缓存中 */
				existingRetriever = this.retrieverCache.putIfAbsent(cacheKey, newRetriever);

				// eg1: 因为之前缓存中没有数据，所以put进新的数据，返回老的数据，即：existingRetriever=null
				if (existingRetriever != null) {
					newRetriever = null;  // no need to populate it in retrieveApplicationListeners
				}
			}
		}

		// eg1: existingRetriever=null
		if (existingRetriever != null) {
			Collection<ApplicationListener<?>> result = existingRetriever.getApplicationListeners();
			if (result != null) {
				return result;
			}
		}

		// eg1: eventType=ResolvableType@2566
		//      sourceType=org.springframework.boot.SpringApplication.class
		//      newRetriever=AbstractApplicationEventMulticaster$CachedListenerRetriever@2216
		return retrieveApplicationListeners(eventType, sourceType, newRetriever); // eg1: 返回值 {LoggingApplicationListener@2232, BackgroundPreinitializer@2233, DelegatingApplicationListener@2234}
	}


	// eg1: eventType=ResolvableType@2566
	//      sourceType=org.springframework.boot.SpringApplication.class
	//      retriever=AbstractApplicationEventMulticaster$CachedListenerRetriever@2216
	/**
	 * 通过给定的事件类型（eventType）和源类型（sourceType），获取响应的应用程序侦听器集合。
	 */
	private Collection<ApplicationListener<?>> retrieveApplicationListeners(ResolvableType eventType,
																			@Nullable Class<?> sourceType,
																			@Nullable CachedListenerRetriever retriever) {

		List<ApplicationListener<?>> allListeners = new ArrayList<>();

		// eg1: filteredListeners=new LinkedHashSet<>();
		Set<ApplicationListener<?>> filteredListeners = (retriever != null ? new LinkedHashSet<>() : null);

		// eg1: filteredListenerBeans=new LinkedHashSet<>();
		Set<String> filteredListenerBeans = (retriever != null ? new LinkedHashSet<>() : null);

		Set<ApplicationListener<?>> listeners;
		Set<String> listenerBeans;
		// eg1: defaultRetriever=new DefaultListenerRetriever();
		synchronized (this.defaultRetriever) {
			// eg1: applicationListeners里保存8个ApplicationListener
			listeners = new LinkedHashSet<>(this.defaultRetriever.applicationListeners);

			// eg1: applicationListenerBeans里保存0个
			listenerBeans = new LinkedHashSet<>(this.defaultRetriever.applicationListenerBeans);
		}

		/**
		 * Add programmatically registered listeners, including ones coming from ApplicationListenerDetector(singleton beans and inner beans).
		 *
		 * listeners = {LinkedHashSet@2255}  size = 8
		 *  0 = {EnvironmentPostProcessorApplicationListener@2154}
		 *  1 = {AnsiOutputApplicationListener@2155}
		 *  2 = {LoggingApplicationListener@2156}
		 *  3 = {BackgroundPreinitializer@2157}
		 *  4 = {DelegatingApplicationListener@2158}
		 *  5 = {ParentContextCloserApplicationListener@2159}
		 *  6 = {ClearCachesApplicationListener@2160}
		 *  7 = {FileEncodingApplicationListener@2161}
		 */
		for (ApplicationListener<?> listener : listeners) {
			// eg1: listener=EnvironmentPostProcessorApplicationListener@2154
			//      eventType=ResolvableType@2566
			//      sourceType=org.springframework.boot.SpringApplication.class
			if (supportsEvent(listener, eventType, sourceType)) {
				if (retriever != null) {
					filteredListeners.add(listener);
				}
				allListeners.add(listener);
			}
		}

		// eg1: listenerBeans.isEmpty() = true
		if (!listenerBeans.isEmpty()) {
			ConfigurableBeanFactory beanFactory = getBeanFactory();
			for (String listenerBeanName : listenerBeans) {
				try {
					if (supportsEvent(beanFactory, listenerBeanName, eventType)) {
						ApplicationListener<?> listener = beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						if (!allListeners.contains(listener) && supportsEvent(listener, eventType, sourceType)) {
							if (retriever != null) {
								if (beanFactory.isSingleton(listenerBeanName)) {
									filteredListeners.add(listener);
								} else {
									filteredListenerBeans.add(listenerBeanName);
								}
							}
							allListeners.add(listener);
						}
					} else {
						// Remove non-matching listeners that originally came from
						// ApplicationListenerDetector, possibly ruled out by additional
						// BeanDefinition metadata (e.g. factory method generics) above.
						Object listener = beanFactory.getSingleton(listenerBeanName);
						if (retriever != null) {
							filteredListeners.remove(listener);
						}
						allListeners.remove(listener);
					}
				} catch (NoSuchBeanDefinitionException ex) {
					// Singleton listener instance (without backing bean definition) disappeared -
					// probably in the middle of the destruction phase
				}
			}
		}

		// eg1:
		// allListeners = {LoggingApplicationListener@2232, BackgroundPreinitializer@2233, DelegatingApplicationListener@2234}
		AnnotationAwareOrderComparator.sort(allListeners);
		// eg1: retriever=AbstractApplicationEventMulticaster$CachedListenerRetriever@2216
		if (retriever != null) {
			// eg1: filteredListenerBeans.isEmpty() = true
			if (filteredListenerBeans.isEmpty()) {
				// eg1: applicationListeners = {LoggingApplicationListener@2232, BackgroundPreinitializer@2233, DelegatingApplicationListener@2234}
				retriever.applicationListeners = new LinkedHashSet<>(allListeners);
				// eg1: applicationListenerBeans={}
				retriever.applicationListenerBeans = filteredListenerBeans;
			} else {
				retriever.applicationListeners = filteredListeners;
				retriever.applicationListenerBeans = filteredListenerBeans;
			}
		}
		// eg1: allListeners = {LoggingApplicationListener@2232, BackgroundPreinitializer@2233, DelegatingApplicationListener@2234}
		return allListeners;
	}

	/**
	 * Filter a bean-defined listener early through checking its generically declared
	 * event type before trying to instantiate it.
	 * <p>If this method returns {@code true} for a given listener as a first pass,
	 * the listener instance will get retrieved and fully evaluated through a
	 * {@link #supportsEvent(ApplicationListener, ResolvableType, Class)} call afterwards.
	 * @param beanFactory the BeanFactory that contains the listener beans
	 * @param listenerBeanName the name of the bean in the BeanFactory
	 * @param eventType the event type to check
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 * @see #supportsEvent(Class, ResolvableType)
	 * @see #supportsEvent(ApplicationListener, ResolvableType, Class)
	 */
	private boolean supportsEvent(
			ConfigurableBeanFactory beanFactory, String listenerBeanName, ResolvableType eventType) {

		Class<?> listenerType = beanFactory.getType(listenerBeanName);
		if (listenerType == null || GenericApplicationListener.class.isAssignableFrom(listenerType) ||
				SmartApplicationListener.class.isAssignableFrom(listenerType)) {
			return true;
		}
		if (!supportsEvent(listenerType, eventType)) {
			return false;
		}
		try {
			BeanDefinition bd = beanFactory.getMergedBeanDefinition(listenerBeanName);
			ResolvableType genericEventType = bd.getResolvableType().as(ApplicationListener.class).getGeneric();
			return (genericEventType == ResolvableType.NONE || genericEventType.isAssignableFrom(eventType));
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Ignore - no need to check resolvable type for manually registered singleton
			return true;
		}
	}

	/**
	 * Filter a listener early through checking its generically declared event
	 * type before trying to instantiate it.
	 * <p>If this method returns {@code true} for a given listener as a first pass,
	 * the listener instance will get retrieved and fully evaluated through a
	 * {@link #supportsEvent(ApplicationListener, ResolvableType, Class)} call afterwards.
	 * @param listenerType the listener's type as determined by the BeanFactory
	 * @param eventType the event type to check
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 */
	protected boolean supportsEvent(Class<?> listenerType, ResolvableType eventType) {
		ResolvableType declaredEventType = GenericApplicationListenerAdapter.resolveDeclaredEventType(listenerType);
		return (declaredEventType == null || declaredEventType.isAssignableFrom(eventType));
	}

	/**
	 * Determine whether the given listener supports the given event.
	 * <p>The default implementation detects the {@link SmartApplicationListener}
	 * and {@link GenericApplicationListener} interfaces. In case of a standard
	 * {@link ApplicationListener}, a {@link GenericApplicationListenerAdapter}
	 * will be used to introspect the generically declared type of the target listener.
	 * @param listener the target listener to check
	 * @param eventType the event type to check against
	 * @param sourceType the source type to check against
	 * @return whether the given listener should be included in the candidates
	 * for the given event type
	 */
	// eg1: listener=EnvironmentPostProcessorApplicationListener@2154
	//      eventType=ResolvableType@2566
	//      sourceType=org.springframework.boot.SpringApplication.class
	protected boolean supportsEvent(ApplicationListener<?> listener, ResolvableType eventType, @Nullable Class<?> sourceType) {
		// eg1: (listener instanceof GenericApplicationListener)=false
		GenericApplicationListener smartListener = (listener instanceof GenericApplicationListener ?
				(GenericApplicationListener) listener : new GenericApplicationListenerAdapter(listener));

		// eg1: smartListener=new GenericApplicationListenerAdapter(EnvironmentPostProcessorApplicationListener@2154))
		//      eventType=ResolvableType@2566
		//      sourceType=org.springframework.boot.SpringApplication.class
		return (smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType));
	}


	/**
	 * Cache key for ListenerRetrievers, based on event type and source type.
	 */
	private static final class ListenerCacheKey implements Comparable<ListenerCacheKey> {

		private final ResolvableType eventType;

		@Nullable
		private final Class<?> sourceType;

		// eg1: eventType=ResolvableType@2566  sourceType=org.springframework.boot.SpringApplication.class
		public ListenerCacheKey(ResolvableType eventType, @Nullable Class<?> sourceType) {
			Assert.notNull(eventType, "Event type must not be null");
			this.eventType = eventType; // eg1: eventType=ResolvableType@2566
			this.sourceType = sourceType; // eg1: sourceType=org.springframework.boot.SpringApplication.class
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ListenerCacheKey)) {
				return false;
			}
			ListenerCacheKey otherKey = (ListenerCacheKey) other;
			return (this.eventType.equals(otherKey.eventType) &&
					ObjectUtils.nullSafeEquals(this.sourceType, otherKey.sourceType));
		}

		@Override
		public int hashCode() {
			return this.eventType.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.sourceType);
		}

		@Override
		public String toString() {
			return "ListenerCacheKey [eventType = " + this.eventType + ", sourceType = " + this.sourceType + "]";
		}

		@Override
		public int compareTo(ListenerCacheKey other) {
			int result = this.eventType.toString().compareTo(other.eventType.toString());
			if (result == 0) {
				if (this.sourceType == null) {
					return (other.sourceType == null ? 0 : -1);
				}
				if (other.sourceType == null) {
					return 1;
				}
				result = this.sourceType.getName().compareTo(other.sourceType.getName());
			}
			return result;
		}
	}


	/**
	 * Helper class that encapsulates a specific set of target listeners,
	 * allowing for efficient retrieval of pre-filtered listeners.
	 * <p>An instance of this helper gets cached per event type and source type.
	 */
	private class CachedListenerRetriever {

		@Nullable
		public volatile Set<ApplicationListener<?>> applicationListeners;

		@Nullable
		public volatile Set<String> applicationListenerBeans;

		@Nullable
		public Collection<ApplicationListener<?>> getApplicationListeners() {
			Set<ApplicationListener<?>> applicationListeners = this.applicationListeners;
			Set<String> applicationListenerBeans = this.applicationListenerBeans;
			if (applicationListeners == null || applicationListenerBeans == null) {
				// Not fully populated yet
				return null;
			}

			List<ApplicationListener<?>> allListeners = new ArrayList<>(
					applicationListeners.size() + applicationListenerBeans.size());
			allListeners.addAll(applicationListeners);
			if (!applicationListenerBeans.isEmpty()) {
				BeanFactory beanFactory = getBeanFactory();
				for (String listenerBeanName : applicationListenerBeans) {
					try {
						allListeners.add(beanFactory.getBean(listenerBeanName, ApplicationListener.class));
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Singleton listener instance (without backing bean definition) disappeared -
						// probably in the middle of the destruction phase
					}
				}
			}
			if (!applicationListenerBeans.isEmpty()) {
				AnnotationAwareOrderComparator.sort(allListeners);
			}
			return allListeners;
		}
	}


	/**
	 * Helper class that encapsulates a general set of target listeners.
	 */
	private class DefaultListenerRetriever {

		public final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

		public final Set<String> applicationListenerBeans = new LinkedHashSet<>();

		public Collection<ApplicationListener<?>> getApplicationListeners() {
			List<ApplicationListener<?>> allListeners = new ArrayList<>(
					this.applicationListeners.size() + this.applicationListenerBeans.size());
			allListeners.addAll(this.applicationListeners);
			if (!this.applicationListenerBeans.isEmpty()) {
				BeanFactory beanFactory = getBeanFactory();
				for (String listenerBeanName : this.applicationListenerBeans) {
					try {
						ApplicationListener<?> listener =
								beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						if (!allListeners.contains(listener)) {
							allListeners.add(listener);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Singleton listener instance (without backing bean definition) disappeared -
						// probably in the middle of the destruction phase
					}
				}
			}
			AnnotationAwareOrderComparator.sort(allListeners);
			return allListeners;
		}
	}

}
