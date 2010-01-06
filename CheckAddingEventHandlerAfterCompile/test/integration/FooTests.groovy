import grails.test.*

class FooTests extends GrailsUnitTestCase {
    protected void setUp() {
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testFramework() { assertTrue(true) }

    void testAddEvent() { 
      def loaded = false

      Foo.metaClass.afterInsert = {->
        loaded = true
      }

      def foo = new Foo()
      assert !loaded

      foo.save(flush:true)
      assert loaded
    }

    void testWrapEvent() {
      def originalAfterDelete = Foo.metaClass.getMetaMethod("afterDelete", [] as Object[])
      assert originalAfterDelete != null
    
      def deleted = false

      Foo.metaClass.afterDelete = {->
        originalAfterDelete.invoke(delegate)
        deleted = true
      }

      def foo = new Foo().save(flush:true)
      assert foo != null

      foo.delete(flush:true)
      assert foo.afterDeleteFired
      assert deleted
    }

}
