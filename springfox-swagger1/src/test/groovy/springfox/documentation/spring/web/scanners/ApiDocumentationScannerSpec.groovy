/*
 *
 *  Copyright 2015-2019 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package springfox.documentation.spring.web.scanners

import springfox.documentation.builders.ApiDescriptionBuilder
import springfox.documentation.builders.ApiListingBuilder
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.ApiKey
import springfox.documentation.service.Documentation
import springfox.documentation.service.ResourceListing
import springfox.documentation.spi.service.contexts.Defaults
import springfox.documentation.spring.web.mixins.RequestMappingSupport
import springfox.documentation.spring.web.plugins.DocumentationContextSpec

import static springfox.documentation.builders.PathSelectors.*

@Mixin([RequestMappingSupport])
class ApiDocumentationScannerSpec extends DocumentationContextSpec {

  ApiListingReferenceScanner listingReferenceScanner = Mock(ApiListingReferenceScanner)
  ApiListingScanner listingScanner = Mock(ApiListingScanner)
  ApiDocumentationScanner docScanner = new ApiDocumentationScanner(listingReferenceScanner, listingScanner)

  def "default swagger resource"() {
    when: "I create a swagger resource"
    listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult(new HashMap<>())
    listingScanner.scan(_) >> new HashMap<>()
    and:
    Documentation scanned = docScanner.scan(documentationContext())

    then: "I should should have the correct defaults"
    ResourceListing resourceListing = scanned.resourceListing
    def apiListingReferenceList = resourceListing.getApis()
    def authorizationTypes = resourceListing.getSecuritySchemes()

    scanned.groupName == "default"
    resourceListing.getApiVersion() == "1.0"

    resourceListing.getInfo() != null
    apiListingReferenceList == []
    authorizationTypes == []
  }

  def "resource with api info"() {
    given:
    ApiInfo expected = new ApiInfo("title", "description", "1.0", "terms", "contact", "license", "licenseUrl")
    when:
    plugin
        .groupName("groupName")
        .select()
        .paths(regex(".*"))
        .build()
        .apiInfo(expected)
        .configure(contextBuilder)
    listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult(new HashMap<>())
    listingScanner.scan(_) >> new HashMap<>()
    and:
    Documentation scanned = docScanner.scan(documentationContext())
    then:
    ApiInfo actual = scanned.getResourceListing().getInfo()
    actual.getTitle() == expected.getTitle()
    actual.getVersion() == expected.getVersion()
    actual.getDescription() == expected.getDescription()
    actual.getTermsOfServiceUrl() == expected.getTermsOfServiceUrl()
    actual.getContact() == expected.getContact()
    actual.getLicense() == expected.getLicense()
    actual.getLicenseUrl() == expected.getLicenseUrl()
  }

  def "resource with authorization types"() {
    given:
    ApiKey apiKey = new ApiKey("my-key", "api_key", "header")
    when:
    plugin
        .groupName("groupName")
        .select()
        .paths(regex(".*"))
        .build()
        .securitySchemes([apiKey])
        .configure(contextBuilder)
    listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult(new HashMap<>())
    listingScanner.scan(_) >> new HashMap<>()
    and:
    Documentation scanned = docScanner.scan(documentationContext())
    then:
    ResourceListing resourceListing = scanned.resourceListing
    def authorizationTypes = resourceListing.getSecuritySchemes()
    def apiKeyAuthType = authorizationTypes[0]
    apiKeyAuthType instanceof ApiKey
    apiKeyAuthType.name == "my-key"
    apiKeyAuthType.keyname == "api_key"
    apiKeyAuthType.passAs == "header"
  }

  def "Should sort based on position"() {
    given:
    def defaults = new Defaults()
    def ordering = defaults.apiListingReferenceOrdering()
    plugin
        .groupName("groupName")
        .select()
        .paths(regex(".*"))
        .build()
        .apiListingReferenceOrdering(ordering)
        .configure(contextBuilder)

    def listingsMap = new HashMap<>()
    def listings = [
        apiListing(defaults, 1, "/b"),
        apiListing(defaults, 2, "/c"),
        apiListing(defaults, 2, "/a"),
    ]
    listings.each {
      listingsMap.putIfAbsent("test", new LinkedList())
      listingsMap.get("test").add(it)
    }
    listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult(new HashMap<>())
    listingScanner.scan(_) >> listingsMap


    when:
    Documentation scanned = docScanner.scan(documentationContext())

    then:
    def firstResouceListingApi = scanned.resourceListing.apis.get(0)
    firstResouceListingApi.path == "/groupName/test"
    firstResouceListingApi.description.normalize() == """Operation with path /a and position 2
                                                             |Operation with path /b and position 1
                                                             |Operation with path /c and position 2""".stripMargin()

    where:
    index | path | position
    0     | '/b' | 0
    1     | '/a' | 0
    2     | '/c' | 0
  }

  def apiListing(Defaults defaults, int position, String path) {
    new ApiListingBuilder(defaults.apiDescriptionOrdering())
        .position(position)
        .apis([new ApiDescriptionBuilder(defaults.operationOrdering())
                   .path(path)
                   .build()])
        .description("Operation with path $path and position $position")
        .build()
  }

}
