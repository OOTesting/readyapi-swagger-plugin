/**
 *  Copyright 2013-2017 SmartBear Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.smartbear.swagger

import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.wsdl.WsdlProject
import com.eviware.soapui.impl.wsdl.WsdlTestSuite
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep
import com.eviware.soapui.impl.wsdl.teststeps.assertions.TestAssertionRegistry
import com.eviware.soapui.plugins.auto.PluginTestAssertion
import com.eviware.soapui.plugins.auto.factories.AutoTestAssertionFactory
import com.eviware.soapui.security.assertion.ValidHttpStatusCodesAssertion
import org.hamcrest.CoreMatchers

import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

/**
 * Basic tests that use the examples at wordnik - if they change these tests will probably break
 *
 * @author Ole Lensmar
 */

class SwaggerImporterTest extends GroovyTestCase {
    void testImportResourceListing() {
        def project = new WsdlProject();

        def file = new File("src/test/resources/resource-listing")
        def importer = SwaggerUtils.createSwaggerImporter(file.toURL().toString(), project)

        assertTrue(importer instanceof Swagger1XResourceListingImporter)

        def service = importer.importSwagger(file.toURL().toString())[0]
        assertEquals(36, service.getResourceList().size())
    }

    void testImportApiDeclaration() {
        def project = new WsdlProject();

        def file = new File("src/test/resources/resource-listing-test/API-Declaration.swagger")
        def importer = SwaggerUtils.createSwaggerImporter(file.toURL().toString(), project)

        assertTrue(importer instanceof Swagger1XApiDeclarationImporter)

        def service = importer.importSwagger(file.toURL().toString())[0]
        assertEquals(36, service.getResourceList().size())

        importer.importSwagger(new File("src/test/resources/api-docs").toURI().toString());
    }

    void testImportSwagger2() {
        def project = new WsdlProject();
        SwaggerImporter importer = new Swagger2Importer(project)
        def url = new File("src/test/resources/default swagger.json").toURI().toURL().toString()

        def restService = importer.importSwagger(url)[0]
        assertEquals(2, restService.endpoints.length)

        importer.importSwagger("src/test/resources/default swagger.yaml")[0]
        def result = importer.importSwagger("src/test/resources/petstore-2.0.json")

        RestService service = result[0]
        assertTrue(service.endpoints.length > 0)
        def restMethod = service.getResourceByFullPath("/v2/store/order").getRestMethodAt(0)
        assertEquals(1, restMethod.getRequestCount())
        assertNotNull(restMethod.getRequestAt(0).getRequestContent())

        assertEquals("http://petstore.swagger.io", service.endpoints[0])
        assertEquals("/v2", service.basePath,)
    }

    void testImportSwagger3() {
        def project = new WsdlProject()

        def yamlUrl = "src/test/resources/petstore-3.0.yaml"
        def jsonUrl = "src/test/resources/petstore-3.0.json"

        OpenAPI3Importer importer = new OpenAPI3Importer(project)

        def restService1 = importer.importSwagger(yamlUrl)[0]
        assertEquals(2, restService1.endpoints.length)
        assertEquals(2, restService1.resources.size())
        assertEquals("http://test.demo", restService1.endpoints[1])
        assertTrue(restService1.getResourceByFullPath("/pets/{petId}").getRestMethodByName("showPetById").getRepresentations().length > 0)

        def restService2 = importer.importSwagger(jsonUrl)[0]
        assertEquals(2, restService2.endpoints.length)
        assertEquals(2, restService2.resources.size())
        assertEquals("http://test.demo", restService2.endpoints[1])
    }

    void testTestCaseGeneration() {
        TestAssertionRegistry.getInstance().addAssertion(new AutoTestAssertionFactory(SwaggerComplianceAssertion.getAnnotation(PluginTestAssertion.class), SwaggerComplianceAssertion.class));
        WsdlProject project = new WsdlProject()
        project.name = 'Rest Project From Swagger'
        Swagger2Importer swagger2Importer = new Swagger2Importer(project, "application/json", true)
        String swaggerUrl = new File("src/test/resources/petstore-2.0.json").toURI().toString()
        swagger2Importer.importSwagger(swaggerUrl)

        //assert test suite it created and number of Test Case is same as number of resources/paths
        assertThat(project.getTestSuiteCount(), CoreMatchers.is(1))
        WsdlTestSuite testSuite = project.getTestSuiteAt(0)
        assertThat(testSuite.getTestCaseCount(), CoreMatchers.is(project.getInterfaceAt(0).getOperationCount()))

        //assert parameters with default value
        RestTestRequestStep testStep = (RestTestRequestStep) testSuite.getTestCaseByName('/pet/findByStatus-TestCase').getTestStepAt(0)
        assertThat(testStep.getTestRequest().getParams().getProperty('status').getValue(), CoreMatchers.is('exampleValue'))
        assertEquals("Request 1: GET /pet/findByStatus", testStep.name)

        testStep = (RestTestRequestStep) testSuite.getTestCaseByName('/pet/{petId}-TestCase').getTestStepAt(0)
        Integer.parseInt(testStep.getTestRequest().getParams().getProperty('petId').getValue())
        assertEquals("Request 1: GET /pet/{petId}", testStep.name)

        testStep = (RestTestRequestStep) testSuite.getTestCaseByName('/user/{username}-TestCase').getTestStepAt(0)
        assertFalse(testStep.getTestRequest().getParams().getProperty('username').getValue().isEmpty())
        assertEquals("Request 1: GET /user/{username}", testStep.name)

        // valid status codes assertion
        assertThat(testStep.getAssertionAt(0).label, CoreMatchers.is(ValidHttpStatusCodesAssertion.LABEL))

        // valid status codes assertion
        SwaggerComplianceAssertion swaggerComplianceAssertion = (SwaggerComplianceAssertion) testStep.getAssertionAt(1)
        assertThat(swaggerComplianceAssertion.label, CoreMatchers.is("Swagger Compliance Assertion"))
        assertThat(swaggerComplianceAssertion.swaggerUrl, CoreMatchers.is(swaggerUrl))
    }
}
