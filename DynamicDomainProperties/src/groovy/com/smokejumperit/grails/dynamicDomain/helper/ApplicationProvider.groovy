package com.smokejumperit.grails.dynamicDomain.helper

import org.codehaus.groovy.grails.commons.ApplicationHolder

/**
* Planning on implementing an optimization for grabbing the Grails application.  Unfortunately, we need to 
* figure out how to hook into reloading in order to do that.
*/
class ApplicationProvider {

  static getApplication() {
    return ApplicationHolder.getApplication()
  }

  static getDomainHandler() {
    return getApplication()?.getArtefactHandler("Domain")
  }
  
}
