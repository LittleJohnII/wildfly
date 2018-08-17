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
package org.jboss.as.test.integration.domain.mixed;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author <a href="rjanik@redhat.com">Richard Janík</a>
 */
public class OperationCompatibilityTestCase extends MixedDomainTestSuite {

    protected static DomainTestSupport support;
    private static ModelControllerClient masterClient;
    private static ModelControllerClient slaveClient;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    /**
     * To be called from @BeforeClass
     */
    protected static void setUpClass() throws TimeoutException, InterruptedException {
        masterClient = support.getDomainMasterLifecycleUtil().getDomainClient();
        slaveClient = support.getDomainSlaveLifecycleUtil().getDomainClient();
        masterLifecycleUtil = support.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = support.getDomainSlaveLifecycleUtil();
        masterLifecycleUtil.start();
        masterLifecycleUtil.awaitServers(System.currentTimeMillis());
    }

    @AfterClass
    public static void tearDownClass() {
        if (support != null) support.stop();
        masterClient = slaveClient = null;
        if (masterLifecycleUtil != null) masterLifecycleUtil.stop();
        if (slaveLifecycleUtil != null) slaveLifecycleUtil.stop();
        masterLifecycleUtil = slaveLifecycleUtil = null;
    }

    @Test
    public void testReadConfigAsFeatures() throws IOException {
        testReadConfigOp(Util.createEmptyOperation(READ_CONFIG_AS_FEATURES_OPERATION, PathAddress.EMPTY_ADDRESS));
        assertDomainIsOk();
    }

    private void testReadConfigOp(ModelNode op) {
        try {
            executeOperation(op, masterClient);
        } catch (IOException e) {
            Assert.fail("operation " + op.get(OP).asString() + " should have passed on master, exception:\n" + e);
        }
        try {
            ModelNode result = executeOperation(op, slaveClient);
            Assert.assertEquals("operation " + op.get(OP).asString() + "should have completed with error on slave, result:\n", result.get(OUTCOME).asString(), FAILED);
        } catch (IOException e) {
            Assert.fail("operation " + op.get(OP).asString() + " should have completed with error on slave, exception:\n" + e);
        }
    }

    private void assertDomainIsOk() throws IOException {
        ModelNode readServerState = Util.getReadAttributeOperation(PathAddress.pathAddress(HOST, "slave").append(SERVER, "server-one"), "server-state");
        ModelNode result = executeOperation(readServerState, slaveClient);
        Assert.assertFalse(result.hasDefined(OUTCOME));
        Assert.assertEquals("running", result.asString());
    }

    private ModelNode executeOperation(ModelNode op, ModelControllerClient client) throws IOException {
        ModelNode response = client.execute(op);
        if (response.hasDefined(OUTCOME) && response.get(OUTCOME).asString().equals(SUCCESS)) {
            return response.get(RESULT);
        } else {
            return response;
        }
    }
}
