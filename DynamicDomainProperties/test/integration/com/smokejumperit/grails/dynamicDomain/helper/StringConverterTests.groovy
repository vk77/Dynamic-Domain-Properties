package com.smokejumperit.grails.dynamicDomain.helper

import grails.test.*
import com.smokejumperit.test.*
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.stringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.destringify

class StringConverterTests extends GrailsUnitTestCase {
    protected void setUp() {
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testFramework() { assertTrue true }

    void testStringifyNull() {
      assertNull stringify(null)
    }

    void testDestringifyNull() {
      assertNull destringify(null, null)
    }

    void testString() {
      def val = stringify("foo")
      assertNotNull val 
      assertEquals val.getClass(), String
      assertEquals val, "foo"
      assertEquals destringify(String, val), "foo"
    }

    void testGString() {
      def bar = "bar"
      def foo = "foo$bar"
      assertTrue GString.isAssignableFrom(foo.getClass())
      def val = stringify(foo)
      assertNotNull val 
      assertEquals val.getClass(), String
      assertEquals val, "foobar"
      assertEquals destringify(GString, val), "foobar"
      assertEquals destringify(foo.getClass(), val), "foobar"
    }

    void testClass() {
      def clazz = GString
      def val = stringify(clazz)
      assertNotNull val
      assertEquals String, val.getClass()
      assertEquals clazz.getName(), val 
      assertEquals clazz, destringify(Class, val)
    }

    void testWithFromString() {
      Class clazz = Eval.me("""
        class WithFromString {

          String value

          String toString() { "foo" }

          static fromString(String ignored) {
            def toReturn = new WithFromString()
            toReturn.value = "bar"
            return toReturn
          }
        }
        return WithFromString
      """)
      def val = stringify(clazz.newInstance())
      assertNotNull val 
      assertEquals String, val.getClass() 
      assertEquals "foo", val 
      def deval = destringify(clazz, val)
      assertEquals clazz, deval.getClass() 
      assertEquals "bar", deval.value 
    }

    void testSavedDomainClass() {
      def foo = new Foo(value:"foo").save(flush:true, failOnError:true)
      def val = stringify(foo)
      assertNotNull val 
      assertEquals String, val.getClass() 
      assertEquals "${foo.id}", val 
      def deval = destringify(Foo, val)
      assertEquals Foo, deval.getClass() 
      assertEquals foo.id, deval.id
      foo.value = "bar"
      foo.save(flush:true, failOnError:true)
    }

}
