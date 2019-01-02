/*
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.metrics.portal.models.ebean;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.typesafe.config.ConfigFactory;
import io.ebean.Ebean;
import models.ebean.PackageVersion;
import models.ebean.VersionSet;
import models.ebean.VersionSpecification;
import models.ebean.VersionSpecificationAttribute;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test for <code>VersionSpecification</code>.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
public class VersionSpecificationTest extends WithApplication {

    @Test
    public void testCreateVersionSpecification() throws Exception {

        final PackageVersion packageVersion1 = new PackageVersion();
        packageVersion1.setName("ironman");
        packageVersion1.setVersion("1.2.3");
        packageVersion1.setUri("some-repository/ironman");
        Ebean.save(packageVersion1);

        final PackageVersion packageVersion2 = new PackageVersion();
        packageVersion2.setName("hulk");
        packageVersion2.setVersion("a.b.c");
        packageVersion2.setUri("some-repository/hulk");
        Ebean.save(packageVersion2);

        final VersionSet versionSet = new VersionSet();
        versionSet.setPackageVersions(Arrays.asList(packageVersion1, packageVersion2));
        versionSet.setUuid(UUID.randomUUID());
        // We specifically allow arbitrary version strings
        versionSet.setVersion("deployment1");
        Ebean.save(versionSet);

        final VersionSpecification spec = new VersionSpecification();
        spec.setUuid(UUID.randomUUID());
        spec.setVersionSet(versionSet);
        Ebean.save(spec);

        final VersionSpecificationAttribute specAttrib1 = new VersionSpecificationAttribute();
        specAttrib1.setKey("colo");
        specAttrib1.setValue("north-america");
        specAttrib1.setVersionSpecification(spec);
        Ebean.save(specAttrib1);

        final VersionSpecificationAttribute specAttrib2 = new VersionSpecificationAttribute();
        specAttrib2.setKey("dogfood-group");
        specAttrib2.setValue("alpha");
        specAttrib2.setVersionSpecification(spec);
        Ebean.save(specAttrib2);

        final List<VersionSpecification> allSpecs = Ebean.find(VersionSpecification.class).findList();

        assertEquals(1, allSpecs.size());

        final Iterable<VersionSpecificationAttribute> attribs = allSpecs.get(0).getVersionSpecificationAttributes();

        final Collection<Matcher<? super VersionSpecificationAttribute>> attribMatchers = new ArrayList<>();
        attribMatchers.add(hasKV("colo", "north-america"));
        attribMatchers.add(hasKV("dogfood-group", "alpha"));
        assertThat(attribs, Matchers.containsInAnyOrder(attribMatchers));
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("play.modules.disabled", Arrays.asList("play.core.ObjectMapperModule", "global.PillarModule"))
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    private Matcher<? super VersionSpecificationAttribute> hasKV(final String k, final String v) {
        final FeatureMatcher<VersionSpecificationAttribute, String> keyMatcher =
                new FeatureMatcher<VersionSpecificationAttribute, String>(Matchers.equalTo(k), "key", "key") {
            @Override
            protected String featureValueOf(final VersionSpecificationAttribute versionSpecificationAttribute) {
                return versionSpecificationAttribute.getKey();
            }
        };
        final FeatureMatcher<VersionSpecificationAttribute, String> valueMatcher =
                new FeatureMatcher<VersionSpecificationAttribute, String>(
                        Matchers.equalTo(v),
                        "value",
                        "value") {
            @Override
            protected String featureValueOf(final VersionSpecificationAttribute versionSpecificationAttribute) {
                return versionSpecificationAttribute.getValue();
            }
        };
        return Matchers.allOf(keyMatcher, valueMatcher);
    }
}
