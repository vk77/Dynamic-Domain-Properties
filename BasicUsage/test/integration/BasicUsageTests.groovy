import grails.test.*

class BasicUsageTests extends GrailsUnitTestCase {
  void testFramework() { assertTrue true }

  void testMostSimpleUsage() {
    def me = new Simpleton()
    me.foo = "Foo!"
    me.baz = [
      list:[1,2,3],
      map:[robertFischer:'http://enfranchisedmind.com/blog/'],
      domainObject:new Simpleton()
    ]
    me.save() // Now persisted to the database
  }

  void testDelegatingToReifiedPropertyUsage() {
    def me = new Delegator()
    me.delegateTo = new Simpleton()
    me.reifiedProperty = "Yup, reified!"
    assertEquals "Yup, reified!", me.delegateTo.reifiedProperty
  }

  void testDelegatingToAnotherDynamicPropertyUsage() {
    def me = new Delegator()
    me.delegateTo = new Simpleton()
    me.delegateTo.foo = "Foo!"
    assertEquals "Foo!", me.foo
  }

}
