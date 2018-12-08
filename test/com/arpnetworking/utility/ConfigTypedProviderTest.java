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
import org.junit.Assert;
import org.junit.Test;
import play.Environment;

interface TestInterface {}
class TestClass implements TestInterface { }

class InjectedClass {}
class TestClassInjected implements TestInterface {
    @Inject
    public TestClassInjected(InjectedClass injected) {
        _injected = injected;
    }

    InjectedClass getInjected() {
        return _injected;
    }

    private InjectedClass _injected;
}
public class ConfigTypedProviderTest {

    @Test
    public void createBasic() {
        final Config config = ConfigFactory.empty()
                .withValue("testinterface.impl.class", ConfigValueFactory.fromAnyRef(TestClass.class.getCanonicalName()));

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
        Assert.assertNotNull(instance);
        Assert.assertEquals(TestClass.class, instance.getClass());
    }

    @Test
    public void createWithInjected() {
        final Config config = ConfigFactory.empty()
                .withValue("testinterface.impl.class", ConfigValueFactory.fromAnyRef(TestClassInjected.class.getCanonicalName()));

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
        Assert.assertNotNull(instance);
        Assert.assertEquals(TestClassInjected.class, instance.getClass());
        final TestClassInjected testClassInjected = (TestClassInjected) instance;
        Assert.assertSame(injectedClass, testClassInjected.getInjected());

    }
}