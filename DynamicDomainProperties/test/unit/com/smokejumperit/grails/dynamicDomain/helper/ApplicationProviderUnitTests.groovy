package com.smokejumperit.grails.dynamicDomain.helper

import grails.test.*
import org.codehaus.groovy.grails.commons.ApplicationHolder

class ApplicationProviderUnitTests extends GrailsUnitTestCase {

  void testFramework() {
    assert true
  }

  void testGetApplication() {
    assert ApplicationProvider.getApplication() == null
    assert ApplicationHolder.getApplication() == ApplicationProvider.getApplication()
  }

  void testGetDomainHandler() {
    assert ApplicationProvider.getDomainHandler() == null
  }
  
}
