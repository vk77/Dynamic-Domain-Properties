import grails.test.*

class FooTests extends GrailsUnitTestCase {
    protected void setUp() {
      super.setUp()
      Foo.list()*.delete(flush:true)
    }

    protected void tearDown() {
      Foo.list()*.delete(flush:true)
      super.tearDown()
    }

    void testFramework() { assertTrue true }

    void testPercentInMiddle() {
      assert 0 == Foo.count()
      new Foo(name:"foobar").save(flush:true)
      assert Foo.findByNameLike("f%bar") != null
    }

}
