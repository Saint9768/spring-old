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

package org.springframework.core.env;

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 由简单字符串数组支持的CommandLinePropertySource的实现类。
 * 目的：
 *   此CommandLinePropertySource实现旨在提供最简单的方法来解析命令行参数。
 *   与所有CommandLinePropertySource实现一样，命令行参数分为两个不同的组：【选项参数】和【非选项参数】，
 *   如下所述（从 SimpleCommandLineArgsParser 的 Javadoc 复制的某些部分）：
 * 1> 使用选项参数
 *    选项参数必须遵守确切的语法：--optName[=optValue]
 *    也就是说，选项必须以“--”为前缀，并且可以指定也可以不指定值。如果指定了值，则名称和值必须用等号 ("=") 分隔，没有空格。该值可以选择为空字符串。
 *    选项参数的有效示例:
 *        --foo
 *        --foo=
 *        --foo=""
 *        --foo=bar
 *        --foo="bar then baz"
 *        --foo=bar,baz,biz
 *    选项参数的无效示例:
 *        -foo
 *        --foo bar
 *        --foo = bar
 *        --foo=bar --foo=baz --foo=biz
 * 2> 使用非选项参数
 *    在命令行中指定的任何和所有没有“--”选项前缀的参数都将被视为“非选项参数”并通过CommandLineArgs.getNonOptionArgs()方法提供。
 *    典型用法:
 *        public static void main(String[] args) {
 *            PropertySource ps = new SimpleCommandLinePropertySource(args);
 *            // ...
 *        }
 * 有关完整的一般用法示例，请参阅CommandLinePropertySource。
 * 超越基础
 * 当需要更全功能的命令行解析时，请考虑使用提供的JOptCommandLinePropertySource，或针对您选择的命令行解析库实现您自己的CommandLinePropertySource。
 */
public class SimpleCommandLinePropertySource extends CommandLinePropertySource<CommandLineArgs> {

	/**
	 * Create a new {@code SimpleCommandLinePropertySource} having the default name
	 * and backed by the given {@code String[]} of command line arguments.
	 * @see CommandLinePropertySource#COMMAND_LINE_PROPERTY_SOURCE_NAME
	 * @see CommandLinePropertySource#CommandLinePropertySource(Object)
	 */
	public SimpleCommandLinePropertySource(String... args) {
		super(new SimpleCommandLineArgsParser().parse(args));
	}

	/**
	 * Create a new {@code SimpleCommandLinePropertySource} having the given name
	 * and backed by the given {@code String[]} of command line arguments.
	 */
	public SimpleCommandLinePropertySource(String name, String[] args) {
		super(name, new SimpleCommandLineArgsParser().parse(args));
	}

	/**
	 * Get the property names for the option arguments.
	 */
	@Override
	public String[] getPropertyNames() {
		return StringUtils.toStringArray(this.source.getOptionNames());
	}

	@Override
	protected boolean containsOption(String name) {
		return this.source.containsOption(name);
	}

	@Override
	@Nullable
	protected List<String> getOptionValues(String name) {
		return this.source.getOptionValues(name);
	}

	@Override
	protected List<String> getNonOptionArgs() {
		return this.source.getNonOptionArgs();
	}

}
