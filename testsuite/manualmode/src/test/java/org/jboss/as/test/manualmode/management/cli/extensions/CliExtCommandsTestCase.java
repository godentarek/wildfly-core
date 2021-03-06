/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.management.cli.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.inject.Inject;

import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.test.integration.management.cli.CliScriptTestBase;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.xnio.IoUtils;

/**
 * Testing commands loaded from the available management model extensions.
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class CliExtCommandsTestCase extends CliScriptTestBase {

    private static final String MODULE_NAME = "test.cli.extension.commands";

    @Inject
    private static ServerController containerController;

    private static TestModule testModule;

    @BeforeClass
    public static void setupServer() throws Exception {
        createTestModule();
        setupServerWithExtension();
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        ModelControllerClient client = null;
        try {
            client = containerController.getClient().getControllerClient();
            ModelNode subsystemResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(CliExtCommandsSubsystemResourceDescription.PATH)));
            ModelNode extensionResult = client.execute(Util.createRemoveOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME)));
            ModelTestUtils.checkOutcome(subsystemResult);
            ModelTestUtils.checkOutcome(extensionResult);
        } finally {
            containerController.stop();
            testModule.remove();
            IoUtils.safeClose(client);
        }
        containerController.stop();
    }

    @Test
    public void testExtensionCommand() throws Exception {

        Assert.assertNotEquals(0,
                execute(containerController.getClient().getMgmtAddress(),
                        containerController.getClient().getMgmtPort(),
                        false, // the command won't be available unless the cli connects to the controller
                        CliExtCommandHandler.NAME,
                        false));

        assertEquals(0,
                execute(containerController.getClient().getMgmtAddress(),
                        containerController.getClient().getMgmtPort(),
                        true,
                        CliExtCommandHandler.NAME,
                        false));
        // the output may contain other logs from the cli initialization
        assertTrue(getLastCommandOutput().endsWith(CliExtCommandHandler.OUTPUT + org.jboss.as.cli.Util.LINE_SEPARATOR));
    }

    private static void createTestModule() throws Exception {
        final File moduleXml = new File(CliExtCommandsTestCase.class.getResource(CliExtCommandsTestCase.class.getSimpleName() + "-module.xml").toURI());
        testModule = new TestModule(MODULE_NAME, moduleXml);

        final JavaArchive archive = testModule.addResource("test-cli-ext-commands-module.jar")
                .addClass(CliExtCommandHandler.class)
                .addClass(CliExtCommandHandlerProvider.class)
                .addClass(CliExtCommandsExtension.class)
                .addClass(CliExtCommandsParser.class)
                .addClass(CliExtCommandsSubsystemResourceDescription.class);

        ArchivePath services = ArchivePaths.create("/");
        services = ArchivePaths.create(services, "services");

        final ArchivePath extService = ArchivePaths.create(services, Extension.class.getName());
        archive.addAsManifestResource(CliExtCommandHandler.class.getPackage(), Extension.class.getName(), extService);

        final ArchivePath cliCmdService = ArchivePaths.create(services, CommandHandlerProvider.class.getName());
        archive.addAsManifestResource(CliExtCommandHandler.class.getPackage(), CommandHandlerProvider.class.getName(), cliCmdService);

        testModule.create(true);
    }

    private static void setupServerWithExtension() throws Exception {
        containerController.start();
        ManagementClient managementClient = containerController.getClient();
        ModelControllerClient client = managementClient.getControllerClient();

        //Add the extension
        final ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.EXTENSION, MODULE_NAME));
        ModelTestUtils.checkOutcome(client.execute(addExtension));

        final ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(CliExtCommandsSubsystemResourceDescription.PATH));
        ModelTestUtils.checkOutcome(client.execute(addSubsystem));
    }
}
