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
import com.smartbear.swagger4j.ResourceListing
import com.smartbear.swagger4j.Swagger

/**
 * Importer for Swagger 1.X resource listings - uses Swagger4j
 *
 * @author Ole Lensmar
 */

class Swagger1XResourceListingImporter extends AbstractSwagger1XImporter {

    public Swagger1XResourceListingImporter(WsdlProject project, String defaultMediaType) {
        super(project, defaultMediaType)
    }

    public Swagger1XResourceListingImporter(WsdlProject project) {
        super(project)
    }

    public RestService[] importSwagger(String url) {

        def result = []

        ResourceListing resourceListing = Swagger.readSwagger(URI.create(url))
        resourceListing.apis.each {

            String name = it.path
            if (name.startsWith("/api-docs")) {
                def ix = name.indexOf("/", 1)
                if (ix > 0)
                    name = name.substring(ix)
            }

            Console.println("Importing API declaration with path $it.path")

            def restService = importApiDeclaration(it.declaration, name)
            ensureEndpoint(restService, url)
            result.add(restService)
        }

        return result.toArray()
    }
}
