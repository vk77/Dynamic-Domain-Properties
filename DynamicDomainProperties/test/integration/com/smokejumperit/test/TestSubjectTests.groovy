package com.smokejumperit.test

import grails.test.*

class TestSubjectTests extends GrailsUnitTestCase {

    def fixture = null

    protected void setUp() {
      super.setUp()
      clearSession()
      fixture = new TestSubject().save(flush:true, failOnError:true)
    }

    protected void tearDown() {
      TestSubject.list()*.delete(flush:true)
      clearSession()
      super.tearDown()
    }

    void testFramework() { assertTrue true }

  static assignProperty(target, String name, value) {
    target."$name" = value
    assertEquals value, target."$name"
    target.save(flush:true, failOnError:true)
    assertEquals value, target."$name"
  }

  static clearSession() {
    TestSubject.withSession { it.flush(); it.clear() }
  }
/*
  void testStringProperty() {
    assignProperty(fixture, "foo", "bar")
    clearSession()
    assertEquals "bar", TestSubject.get(fixture.id).foo
  }
*/

  void testHasDynamicProperties() { assertTrue fixture.hasDynamicProperties() }

}
