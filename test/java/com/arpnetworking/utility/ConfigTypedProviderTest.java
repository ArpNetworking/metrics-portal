/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.utility;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.Test;
import play.Environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Tests for {@link ConfigTypedProvider}.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public final class ConfigTypedProviderTest {

    @Test
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    public void createBasic() {
        final Config config = ConfigFactory.empty()
                .withValue("testinterface.impl.class", ConfigValueFactory.fromAnyRef(TestClass.class.getName()));

        final AbstractModule module = new AbstractModule() {
            @Override
            protected void configure() {
                bind(Environment.class).toInstance(Environment.simple());
                bind(TestInterface.class).toProvider(ConfigTypedProvider.provider("testinterface.impl.class"));
                bind(Config.class).toInstance(config);
            }
        };

        final Injector injector = Guice.createInjector(module);
        final TestInterface instance = injector.getInstance(TestInterface.class);
        assertNotNull(instance);
        assertEquals(TestClass.class, instance.getClass());
    }

    @Test
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    public void createWithInjected() {
        final Config config = ConfigFactory.empty()
                .withValue("testinterface.impl.class", ConfigValueFactory.fromAnyRef(TestClassInjected.class.getName()));

        final InjectedClass injectedClass = new InjectedClass();

        final AbstractModule module = new AbstractModule() {
            @Override
            protected void configure() {
                bind(Environment.class).toInstance(Environment.simple());
                bind(TestInterface.class).toProvider(ConfigTypedProvider.provider("testinterface.impl.class"));
                bind(Config.class).toInstance(config);
                bind(InjectedClass.class).toInstance(injectedClass);
            }
        };

        final Injector injector = Guice.createInjector(module);
        final TestInterface instance = injector.getInstance(TestInterface.class);
        assertNotNull(instance);
        assertEquals(TestClassInjected.class, instance.getClass());
        final TestClassInjected testClassInjected = (TestClassInjected) instance;
        assertSame(injectedClass, testClassInjected.getInjected());
    }

    /**
     * @author Gilligan Markham (gmarkham at dropbox dot com)
     */
    public interface TestInterface {}

    /**
     * @author Gilligan Markham (gmarkham at dropbox dot com)
     */
    public static class InjectedClass {}

    /**
     * @author Gilligan Markham (gmarkham at dropbox dot com)
     */
    public static class TestClass implements TestInterface { }

    /**
     * @author Gilligan Markham (gmarkham at dropbox dot com)
     */
    public static class TestClassInjected implements TestInterface {
        @Inject
        public TestClassInjected(final InjectedClass injected) {
            _injected = injected;
        }

        InjectedClass getInjected() {
            return _injected;
        }

        private InjectedClass _injected;
    }
}
