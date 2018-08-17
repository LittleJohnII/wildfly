/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.management.api;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.base.ContainerResourceMgmtTestBase;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * Smoke test for {@code read-config-as-features} operation with subsystems not available in WFCORE.
 *
 * @author <a href="rjanik@redhat.com">Richard Janík</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ReadConfigAsFeaturesTestCase extends ContainerResourceMgmtTestBase {

    private ReadConfigAsFeaturesTestSupport testSupport = new ReadConfigAsFeaturesTestSupport();

    @Before
    public void setUp() {
        testSupport.saveDefaultConfig();
        testSupport.saveDefaultResult();
        testSupport.saveNonNestedResult();
    }

    @After
    public void tearDown() {
        testSupport.restoreDefaultConfig();
    }

    @Test
    public void nonCoreSubsystemTest() throws IOException, MgmtOperationException {
        ModelNode removeHibernateCache = Util.createRemoveOperation(PathAddress.pathAddress(SUBSYSTEM, "infinispan").append("cache-container", "hibernate"));
        ModelNode addLocalCache = Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, "infinispan").append("cache-container", "web").append("local-cache", "custom"));
        addLocalCache.get("jndi-name").set("custom-jndi-name");
        addLocalCache.get("statistics-enabled").set("true");

        ModelNode expectedReadConfigAsFeatures = testSupport.defaultResult.clone();

        // remove the hibernate cache container
        ModelNode infinispanSubsystem = testSupport.getFeatureNodeChild(expectedReadConfigAsFeatures.get(0), "subsystem.infinispan");
        int hibernateCacheContainerIndex = testSupport.getFeatureNodeChildIndex(infinispanSubsystem, "subsystem.infinispan.cache-container", getSimpleModelNode("cache-container", "hibernate"));
        infinispanSubsystem.get(CHILDREN).remove(hibernateCacheContainerIndex);

        // add the custom infinispan cache
        ModelNode localCache = new ModelNode();
        ModelNode localCacheParams = new ModelNode();
        ModelNode localCacheChildren = new ModelNode().add();
        localCacheParams.get("statistics-enabled").set(true);
        localCacheParams.get("jndi-name").set("custom-jndi-name");
        localCache.get(SPEC).set("subsystem.infinispan.cache-container.local-cache");
        localCache.get(ID).set(getSimpleModelNode("local-cache", "custom"));
        localCache.get(PARAMS).set(localCacheParams);
        localCache.get(CHILDREN).set(localCacheChildren);
        localCache.get(CHILDREN).add(getSimpleFeatureNode("subsystem.infinispan.cache-container.local-cache.store.none", getSimpleModelNode("store", "none")));
        localCache.get(CHILDREN).add(getSimpleFeatureNode("subsystem.infinispan.cache-container.local-cache.memory.object", getSimpleModelNode("memory", "object")));
        localCache.get(CHILDREN).add(getSimpleFeatureNode("subsystem.infinispan.cache-container.local-cache.component.transaction", getSimpleModelNode("component", "transaction")));
        localCache.get(CHILDREN).add(getSimpleFeatureNode("subsystem.infinispan.cache-container.local-cache.component.locking", getSimpleModelNode("component", "locking")));
        localCache.get(CHILDREN).add(getSimpleFeatureNode("subsystem.infinispan.cache-container.local-cache.component.expiration", getSimpleModelNode("component", "expiration")));
        ModelNode webCacheContainer = testSupport.getFeatureNodeChild(infinispanSubsystem, "subsystem.infinispan.cache-container", getSimpleModelNode("cache-container", "web"));
        webCacheContainer.get(CHILDREN).add(localCache);

        doTest(Arrays.asList(removeHibernateCache, addLocalCache), expectedReadConfigAsFeatures);
    }

    @Test
    @Ignore("needs WFCORE upgrade to a version higher than 6.0.0.Beta1")
    public void nonNestedTest() {
//        testSupport.ensureNoNestedSpecs(testSupport.defaultNonNestedResult);
    }

    private ModelNode executeForResult(ModelNode op) throws IOException, MgmtOperationException {
        ModelNode result = executeOperation(op);
        Assert.assertTrue(result.isDefined());
        return result;
    }

    private ModelNode getConfigAsFeatures() throws IOException, MgmtOperationException {
        return getConfigAsFeatures(true, PathAddress.EMPTY_ADDRESS);
    }

    private ModelNode getConfigAsFeatures(boolean nested) throws IOException, MgmtOperationException {
        return getConfigAsFeatures(nested, PathAddress.EMPTY_ADDRESS);
    }

    private ModelNode getConfigAsFeatures(PathAddress address) throws IOException, MgmtOperationException {
        return getConfigAsFeatures(true, address);
    }

    private ModelNode getConfigAsFeatures(boolean nested, PathAddress address) throws IOException, MgmtOperationException {
        ModelNode getConfigAsFeatures = Util.createEmptyOperation(READ_CONFIG_AS_FEATURES_OPERATION, address);
        getConfigAsFeatures.get(NESTED).set(nested);
        return executeForResult(getConfigAsFeatures);
    }

    private void doTest(List<ModelNode> operations, ModelNode expectedModel) throws IOException, MgmtOperationException {
        for (ModelNode operation : operations) {
            executeOperation(operation);
        }
        if (!testSupport.equalsWithoutListOrder(expectedModel, getConfigAsFeatures())) {
            System.out.println("Actual:\n" + getConfigAsFeatures().toJSONString(false) + "\nExpected:\n" + expectedModel.toJSONString(false));
            Assert.fail("There are differences between the expected and the actual model, see the test output for details");
        }
    }

    private ModelNode getSimpleModelNode(String key, String value) {
        ModelNode node = new ModelNode();
        node.get(key).set(value);
        return node;
    }

    private ModelNode getSimpleFeatureNode(String spec, ModelNode id) {
        ModelNode node = new ModelNode();
        node.get(SPEC).set(spec);
        node.get(ID).set(id);
        return node;
    }

    private class ReadConfigAsFeaturesTestSupport extends ReadConfigAsFeaturesTestBase {

        private String defaultConfig;
        private ModelNode defaultResult;
        private ModelNode defaultNonNestedResult;

        private int serverBootTimeoutMillis = 180_000;

        @Override
        protected void saveDefaultConfig() {
            if (defaultConfig == null) {
                try {
                    executeForResult(Util.createEmptyOperation(TAKE_SNAPSHOT_OPERATION, PathAddress.EMPTY_ADDRESS));
                    ModelNode result = executeForResult(Util.createEmptyOperation(LIST_SNAPSHOTS_OPERATION, PathAddress.EMPTY_ADDRESS));
                    defaultConfig = result.get(NAMES).asList().get(0).asString();
                } catch (IOException | MgmtOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        protected void saveDefaultResult() {
            if (defaultResult == null) {
                try {
                    defaultResult = getConfigAsFeatures();
                } catch (IOException | MgmtOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // TODO: add @Override: this overrides a method in WFCORE, but that method will only come with WFCORE dependency update
        protected void saveNonNestedResult() {
            if (defaultNonNestedResult == null) {
                try {
                    defaultNonNestedResult = getConfigAsFeatures(false);
                } catch (IOException | MgmtOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        protected void restoreDefaultConfig() {
            ModelNode reload = Util.createEmptyOperation(RELOAD, PathAddress.EMPTY_ADDRESS);
            reload.get(SERVER_CONFIG).set(defaultConfig);
            try {
                executeOperation(reload);
                waitForServerStart(TimeoutUtil.adjust(serverBootTimeoutMillis)); // 3 minutes
            } catch (IOException | MgmtOperationException | TimeoutException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void waitForServerStart(long timeout) throws TimeoutException, InterruptedException {
            ModelNode getServerStatus = Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, "server-state");
            ModelNode result = new ModelNode();
            long deadLine = System.currentTimeMillis() + timeout;
            long delay = 1_000L;
            do {
                if (System.currentTimeMillis() > deadLine) {
                    throw new TimeoutException("timed out waiting for server to boot in " + TimeoutUtil.adjust(serverBootTimeoutMillis) + " milliseconds");
                }
                try {
                    result = executeOperation(getServerStatus);
                } catch (IOException | MgmtOperationException e) {
                    // ok, just retry
                }
                Thread.sleep(delay);
                delay = Math.min(delay * 2, 10_000);
            } while (!(result.isDefined() && result.asString().equals("running")));
        }
    }
}
