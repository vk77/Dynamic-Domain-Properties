package com.smokejumperit.test

import grails.test.*

class TestSubjectTests extends GrailsUnitTestCase {

    def fixture = null

    protected void setUp() {
      println "Running next test"
      super.setUp()
      clearSession()
      fixture = new TestSubject().save(failOnError:true)
      assertNotNull fixture
      TestSubject.withSession { it.flush() }
      assertNotNull fixture.id
    }

    protected void tearDown() {
      super.tearDown()
      println "Done running test"
    }

    void testFramework() { assertTrue true }

  static assignProperty(target, String name, value) {
    target."$name" = value
    assertEquals value, target."$name"
    target.save(flush:true, failOnError:true)
    assertNotNull target
    assertNotNull target.id
    assertEquals value, target."$name"
  }

  static clearSession() {
    TestSubject.withSession { it.clear() }
  }

  void testStringProperty() { doTestProperty("bar") }

  void testIntegerProperty() { doTestProperty(1) }

  void testListWithSublistProperty() { doTestProperty([1,2,[3,4]]) }
  
  void testListProperty() { doTestProperty([1,2,3]) }
  
  void testMapProperty() { doTestProperty([foo:'bar', baz:6]) }

  void testMapWithListProperty() { doTestProperty([foo:[1,2,3]]) }

  void testDomainClassProperty() { doTestProperty(new Token()) }

  void testNullProperty() { doTestProperty(null) }

  void doTestProperty(value) {
    println "Testing property with value $value (${value?.getClass()})"
    assignProperty(fixture, "foo", value)
    clearSession()
    println "Assigned the property successfully; now retrieving the value"
    assertEquals value, TestSubject.get(fixture.id).foo
  }

  void testHasDynamicProperties() { assertTrue fixture.hasDynamicProperties() }

}
