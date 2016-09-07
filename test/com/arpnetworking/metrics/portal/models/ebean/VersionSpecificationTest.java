/**
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

import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.avaje.ebean.Ebean;
import models.ebean.PackageVersion;
import models.ebean.VersionSet;
import models.ebean.VersionSpecification;
import models.ebean.VersionSpecificationAttribute;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Test for <code>VersionSpecification</code>
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
public class VersionSpecificationTest extends WithApplication {

    @Test
    public void testCreateVersionSpecification() throws Exception {

        PackageVersion packageVersion1 = new PackageVersion();
        packageVersion1.setName("ironman");
        packageVersion1.setVersion("1.2.3");
        packageVersion1.setUri("some-repository/ironman");
        Ebean.save(packageVersion1);

        PackageVersion packageVersion2 = new PackageVersion();
        packageVersion2.setName("hulk");
        packageVersion2.setVersion("a.b.c");
        packageVersion2.setUri("some-repository/hulk");
        Ebean.save(packageVersion2);

        VersionSet versionSet = new VersionSet();
        versionSet.setPackageVersions(Arrays.asList(packageVersion1, packageVersion2));
        versionSet.setUuid(UUID.randomUUID());
        // We specifically allow arbitrary version strings
        versionSet.setVersion("deployment1");
        Ebean.save(versionSet);

        VersionSpecification spec = new VersionSpecification();
        spec.setUuid(UUID.randomUUID());
        spec.setVersionSet(versionSet);
        Ebean.save(spec);

        VersionSpecificationAttribute specAttrib1 = new VersionSpecificationAttribute();
        specAttrib1.setKey("colo");
        specAttrib1.setValue("north-america");
        specAttrib1.setVersionSpecification(spec);
        Ebean.save(specAttrib1);

        VersionSpecificationAttribute specAttrib2 = new VersionSpecificationAttribute();
        specAttrib2.setKey("dogfood-group");
        specAttrib2.setValue("alpha");
        specAttrib2.setVersionSpecification(spec);
        Ebean.save(specAttrib2);

        List<VersionSpecification> allSpecs = Ebean.find(VersionSpecification.class).findList();

        Assert.assertEquals(1, allSpecs.size());

        Iterable<VersionSpecificationAttribute> attribs = allSpecs.get(0).getVersionSpecificationAttributes();

        Collection<Matcher<? super VersionSpecificationAttribute>> attribMatchers = new ArrayList<>();
        attribMatchers.add(hasKV("colo", "north-america"));
        attribMatchers.add(hasKV("dogfood-group", "alpha"));
        Assert.assertThat(attribs, Matchers.containsInAnyOrder(attribMatchers));
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    private Matcher<? super VersionSpecificationAttribute> hasKV(final String k, final String v) {
        FeatureMatcher<VersionSpecificationAttribute, String> keyMatcher = new FeatureMatcher<VersionSpecificationAttribute, String>(Matchers.equalTo(k), "key", "key") {
            @Override
            protected String featureValueOf(VersionSpecificationAttribute versionSpecificationAttribute) {
                return versionSpecificationAttribute.getKey();
            }
        };
        FeatureMatcher<VersionSpecificationAttribute, String> valueMatcher = new FeatureMatcher<VersionSpecificationAttribute, String>(Matchers.equalTo(v), "value", "value") {
            @Override
            protected String featureValueOf(VersionSpecificationAttribute versionSpecificationAttribute) {
                return versionSpecificationAttribute.getValue();
            }
        };
        return Matchers.allOf(keyMatcher, valueMatcher);
    }
}
