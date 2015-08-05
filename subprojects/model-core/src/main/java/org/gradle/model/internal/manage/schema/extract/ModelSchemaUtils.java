/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.*;
import groovy.lang.GroovyObject;
import org.gradle.api.Nullable;
import org.gradle.internal.reflect.MethodSignatureEquivalence;

import java.lang.reflect.Method;
import java.util.*;

public class ModelSchemaUtils {
    private static final Equivalence<Method> METHOD_EQUIVALENCE = new MethodSignatureEquivalence();

    private static final Set<Equivalence.Wrapper<Method>> IGNORED_METHODS = ImmutableSet.copyOf(
        Iterables.transform(
            Iterables.concat(
                Arrays.asList(Object.class.getMethods()),
                Arrays.asList(GroovyObject.class.getMethods())
            ), new Function<Method, Equivalence.Wrapper<Method>>() {
                public Equivalence.Wrapper<Method> apply(@Nullable Method input) {
                    return METHOD_EQUIVALENCE.wrap(input);
                }
            }
        )
    );

    /**
     * Returns all candidate methods for schema generation declared by the given type and its super-types.
     *
     * <p>Overriding methods are <em>not</em> folded like in the case of {@link Class#getMethods()}. This allows
     * the caller to identify annotations declared at different levels in the hierarchy, and also to identify all
     * the classes declaring a certain method.</p>
     *
     * <p>Method candidates exclude:</p>
     * <ul>
     *     <li>methods defined by {@link Object} and their overrides</li>
     *     <li>methods defined by {@link GroovyObject} and their overrides</li>
     *     <li>synthetic methods</li>
     * </ul>
     */
    public static List<Method> getCandidateMethods(Class<?> clazz) {
        List<Method> methods = Lists.newArrayList();
        Set<Class<?>> seenInterfaces = Sets.newHashSet();
        Deque<Class<?>> queue = new ArrayDeque<Class<?>>();
        queue.add(clazz);
        while (!queue.isEmpty()) {
            Class<?> type = queue.removeFirst();

            // Do not process Object's or GroovyObject's methods
            if (type.equals(Object.class) || type.equals(GroovyObject.class)) {
                continue;
            }

            for (Method method : type.getDeclaredMethods()) {
                // Ignore generated methods
                if (method.isSynthetic()) {
                    continue;
                }

                // Ignore overrides of Object and GroovyObject methods
                if (IGNORED_METHODS.contains(METHOD_EQUIVALENCE.wrap(method))) {
                    continue;
                }

                methods.add(method);
            }

            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.addLast(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.addLast(iface);
                }
            }
        }
        return methods;
    }
}
