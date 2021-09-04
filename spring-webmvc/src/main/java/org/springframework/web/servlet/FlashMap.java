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

package org.springframework.web.servlet;

import java.util.HashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * FlashMap为一个请求提供了一种存储用于另一个请求的属性的方法。当从一个URL重定向到另一个URL时，这是最常用的——例如:发布/重定向/获取模式。
 * FlashMap在重定向之前保存（通常在会话中），在重定向之后可用并立即删除。
 * 可以使用请求路径和请求参数来设置FlashMap，以帮助识别目标请求。 如果没有此信息，FlashMap可用于下一个请求，该请求可能是也可能不是预期的接收者。
 * 在重定向时，目标URL是已知的，FlashMap可以使用该信息进行更新。 当使用org.springframework.web.servlet.view.RedirectView时，这是自动完成的。
 *
 * 注意：带注释的控制器通常不会直接使用FlashMap。 有关在带注释的控制器中使用flash属性的概述，请参阅org.springframework.web.servlet.mvc.support
 * .RedirectAttributes。
 *
 * 首先先讲解FlashMap，FlashMapManager存在的价值；做过Web开发的人都知道，后端有请求转发和请求重定向两种方式，请求转发的时候Request是同一个，
 * 所以可以在转发后拿到转发前的所有信息；但是重定向后Request是新的，如果需要在重定向前设置一些信息，重定向后获取使用应该怎么办法呢？这就是
 * FlashMap存在的意义，FlashMap借助session重定向前通过FlashMapManager将信息放入FlashMap,重定向后再借助FlashMapManager从session中找
 * 到重定向后需要的FalshMap。
 */
@SuppressWarnings("serial")
public final class FlashMap extends HashMap<String, Object> implements Comparable<FlashMap> {

	@Nullable
	private String targetRequestPath;

	private final MultiValueMap<String, String> targetRequestParams = new LinkedMultiValueMap<>(3);

	private long expirationTime = -1;


	/**
	 * Provide a URL path to help identify the target request for this FlashMap.
	 * <p>The path may be absolute (e.g. "/application/resource") or relative to the
	 * current request (e.g. "../resource").
	 */
	public void setTargetRequestPath(@Nullable String path) {
		this.targetRequestPath = path;
	}

	/**
	 * Return the target URL path (or {@code null} if none specified).
	 */
	@Nullable
	public String getTargetRequestPath() {
		return this.targetRequestPath;
	}

	/**
	 * Provide request parameters identifying the request for this FlashMap.
	 * @param params a Map with the names and values of expected parameters
	 */
	public FlashMap addTargetRequestParams(@Nullable MultiValueMap<String, String> params) {
		if (params != null) {
			params.forEach((key, values) -> {
				for (String value : values) {
					addTargetRequestParam(key, value);
				}
			});
		}
		return this;
	}

	/**
	 * Provide a request parameter identifying the request for this FlashMap.
	 * @param name the expected parameter name (skipped if empty)
	 * @param value the expected value (skipped if empty)
	 */
	public FlashMap addTargetRequestParam(String name, String value) {
		if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
			this.targetRequestParams.add(name, value);
		}
		return this;
	}

	/**
	 * Return the parameters identifying the target request, or an empty map.
	 */
	public MultiValueMap<String, String> getTargetRequestParams() {
		return this.targetRequestParams;
	}

	/**
	 * Start the expiration period for this instance.
	 * @param timeToLive the number of seconds before expiration
	 */
	public void startExpirationPeriod(int timeToLive) {
		this.expirationTime = System.currentTimeMillis() + timeToLive * 1000;
	}

	/**
	 * Set the expiration time for the FlashMap. This is provided for serialization
	 * purposes but can also be used instead {@link #startExpirationPeriod(int)}.
	 * @since 4.2
	 */
	public void setExpirationTime(long expirationTime) {
		this.expirationTime = expirationTime;
	}

	/**
	 * Return the expiration time for the FlashMap or -1 if the expiration
	 * period has not started.
	 * @since 4.2
	 */
	public long getExpirationTime() {
		return this.expirationTime;
	}

	/**
	 * Return whether this instance has expired depending on the amount of
	 * elapsed time since the call to {@link #startExpirationPeriod}.
	 */
	public boolean isExpired() {
		return (this.expirationTime != -1 && System.currentTimeMillis() > this.expirationTime);
	}


	/**
	 * Compare two FlashMaps and prefer the one that specifies a target URL
	 * path or has more target URL parameters. Before comparing FlashMap
	 * instances ensure that they match a given request.
	 */
	@Override
	public int compareTo(FlashMap other) {
		int thisUrlPath = (this.targetRequestPath != null ? 1 : 0);
		int otherUrlPath = (other.targetRequestPath != null ? 1 : 0);
		if (thisUrlPath != otherUrlPath) {
			return otherUrlPath - thisUrlPath;
		}
		else {
			return other.targetRequestParams.size() - this.targetRequestParams.size();
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof FlashMap)) {
			return false;
		}
		FlashMap otherFlashMap = (FlashMap) other;
		return (super.equals(otherFlashMap) &&
				ObjectUtils.nullSafeEquals(this.targetRequestPath, otherFlashMap.targetRequestPath) &&
				this.targetRequestParams.equals(otherFlashMap.targetRequestParams));
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + ObjectUtils.nullSafeHashCode(this.targetRequestPath);
		result = 31 * result + this.targetRequestParams.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "FlashMap [attributes=" + super.toString() + ", targetRequestPath=" +
				this.targetRequestPath + ", targetRequestParams=" + this.targetRequestParams + "]";
	}

}
