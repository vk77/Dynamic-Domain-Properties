package com.smokejumperit.grails.dynamicDomain.helper

import static org.codehaus.groovy.grails.commons.GrailsClassUtils.*
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.stringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.destringify
import static com.smokejumperit.grails.dynamicDomain.helper.MethodWrapper.wrapInstanceNoParmMethod
import static grails.util.GrailsUtil.deepSanitize

import org.apache.commons.collections.*
import org.apache.commons.collections.map.*
import com.smokejumperit.grails.dynamicDomain.DynamicProperty as DynProp
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.runtime.metaclass.*
import jconch.cache.*

class DynamicDomainMethods {

  static logAndReturn(String msg, value) {
    println "Returning ${value} (${value?.getClass()}) -- $msg"
    return value
  }
  
  static applyMethodsTo(applyTo) {
    assert applyTo 
    def clazz = applyTo instanceof Class ? applyTo : applyTo.getClass()
    def dynProps = getStaticPropertyValue(clazz, "dynamicProperties")
    switch(dynProps) {
      case null: return
      case Boolean: case Boolean.TYPE:
        if(!dynProps) return  // static dynamicProperties = false
        dynProps = []         // static dynamicProperties = true 
        break
      case List: break
      default:
        throw deepSanitize(
          new RuntimeException("Don't know how to deal with a 'dynamicProperties' properties with value '$dynProps' (${dynProps?.getClass()})")
        )
    }
    if(clazz != applyTo && applyTo.id) {
      applyToWithId(applyTo.metaClass, dynProps)
    } else {
      applyToNoId(applyTo.metaClass, dynProps)
    }
  }

  static applyEvents(Class clazz) {
    def mc = clazz.metaClass
    [
      onLoad:{-> println "Within 'onLoad' body for $delegate"; applyMethodsTo(delegate) },
      afterInsert:doInsert.clone(),
      afterUpdate:doInsert.clone(),
      afterDelete:doDelete.clone()
    ].each { name, impl ->
      wrapInstanceNoParmMethod(mc, "$name", impl)
    }
  }

  private static final doInsert = {->
    def target = delegate
    assert !(target instanceof Class)
    DynProp.withNewSession { session ->
      DynProp.withTransaction {
        target.getLocalDynamicProperties().values()*.save(failOnError:true)
        session.flush()
      }
    }
    applyMethodsTo(target)
  }

  private static final doDelete = {->
    def doDeleteImpl
    doDeleteImpl = { 
      def children = DynProp.findAllByParent(it) ?: []
      children.each(doDeleteImpl)
      children*.delete()
    }
    def target = delegate
    assert !(target instanceof Class)
    DynProp.withNewSession { session ->
      DynProp.withTransaction { 
        doDeleteImpl(it)
        session.flush()
      }
    }
  }

  static applyToNoId(MetaClass mc, List delegateProps) {
    def reload = { target ->
      assert target.id
      def toDoInsert = doInsert.clone()
      toDoInsert.delegate = target
      toDoInsert()
      applyMethodsTo(target)
    }
    def localCache = new GroovyCacheMap({ key -> [:] })
    mc.getLocalDynamicProperties = { -> localCache[delegate] }
    mc.addLocalDynamicProperty = { DynProp prop ->
      return localCache[delegate][prop.propertyName] = prop
    }
    mc.getLocalDynamicProperty = { String name -> 
      if(delegate.id) {
        reload(delegate)
        return logAndReturn("Found a record that had an id but didn't know it (get)", 
          delegate.getLocalDynamicProperty(name))
      } else {
        return logAndReturn("getLocalDynamicProperty w/no id", localCache[delegate][name]) 
      }
    }
    mc.setLocalDynamicProperty = { String name, value -> 
      if(delegate.id) {
        reload(delegate)
        return logAndReturn("Found a record that had an id but didn't know it (set)", 
          delegate.setLocalDynamicProperty(name, value))
      } else {
        return logAndReturn("setLocalDynamicProperty w/no id", addLocalDynamicProperty(name, value)) 
      }
    }

    applyToCommon(mc, delegateProps)
  }

  static applyToWithId(MetaClass mc, List delegateProps) {
    mc.getLocalDynamicProperties = {->
      return DynProp.findAllByParent(delegate)?.inject([:]) {memo, entry ->
        memo[entry.propertyName] = entry
        return memo
      } ?: [:]
    }
    mc.addLocalDynamicProperty = { DynProp prop ->
      prop.parent = delegate
      try {
        return prop.save(failOnError:true)
      } catch(Exception e) {
        throw deepSanitize(new RuntimeException("Could not save $prop: ${prop.errors.toString()}", deepSanitize(e)))
      }
    }
    mc.getLocalDynamicProperty = {String name->
      assert delegate?.id
      def toReturn = DynProp.query("parentClassValue = :pclass and parentId = :pid and propertyName = :name",
        [pclass:delegate.getClass().name, pid:stringify(delegate.id), name:name]
      )
      if(toReturn) toReturn = toReturn[0]
      return logAndReturn("getLocalDynamicProperty w/id: \"${toReturn.propertyValue}\"", toReturn)
    }
    mc.setLocalDynamicProperty = { String name, value ->
      def prop = getLocalDynamicProperty(name)
      if(prop) {
        prop.propertyValue = value
        try {
          prop.save(failOnError:true)
        } catch(Exception e) {
          throw deepSanitize(new RuntimeException("Could not save $prop: ${prop.errors.toString()}", deepSanitize(e)))
        }
      } else {
        addLocalDynamicProperty(name, value) 
      }
      return value
    }
    applyToCommon(mc, delegateProps)
  }

  private static checkHasParticulars(MetaClass mc) {
    def methods = mc.methods.inject([]) { memo, method ->
      memo << [ name: method.name, nativeParameterTypes: method.nativeParameterTypes]
      return memo
    }
    [
      ["getLocalDynamicProperties", []],
      ["addLocalDynamicProperty", [DynProp]],
      ["getLocalDynamicProperty", [String]],
      ["setLocalDynamicProperty", [String, Object]]
    ].each { name, parmTypes ->
      parmTypes = parmTypes as Class[]
      def method = methods.find { it.name == name && it.nativeParameterTypes == parmTypes }
      assert method
      methods.remove(method)
    }
  }

  private static applyToCommon(MetaClass mc, List delegateProps) {
    checkHasParticulars(mc)
    
    mc.hasDynamicProperties = {-> true }
    mc.getLocalDynamicPropertiesMap = {-> 
      def toReturn = [:] 
      delegate.getLocalDynamicProperties().values().each { v -> toReturn[v.propertyName] = v.propertyValue }
      return Collections.unmodifiableMap(toReturn)
    }  
    mc.addLocalDynamicProperty = { String name, value ->
      def dynProp = new DynProp()
      dynProp.propertyName = name
      dynProp.propertyValue = value
      addLocalDynamicProperty(dynProp)
    }
    mc.getLocalDynamicPropertiesMap = {->
      def toReturn = [:]
      getLocalDynamicProperties().values().each { v -> toReturn[v.propertyName] = v.propertyValue }
      return Collections.unmodifiableMap(toReturn)
    }
    mc.getLocalDynamicPropertyValue = { String name -> getLocalDynamicProperty(name)?.propertyValue }
    mc.hasLocalDynamicProperty = { String name -> getLocalDynamicProperty(name) != null }

    mc.getDynamicPropertyValue = { String name ->
      def me = delegate
      def found

      found = delegate.getLocalDynamicProperty(name)
      if(found) return logAndReturn("Found from delegate", found.propertyValue)

      found = delegateProps.find { prop ->
        return me."$prop"?.metaClass?.hasProperty(propVal, name) 
      }
      if(found) return logAndReturn("Found as property of $found", delegate."$found"."$name")

      found = delegateProps.findAll { me."$it"?.hasDynamicProperties() }?.find { prop ->
        return me."$prop"?.hasLocalDynamicProperty(name)
      }
      if(found) return logAndReturn("Found as local dynamic property of $found", delegate."$found".getLocalDynamicProperty(name))

      return logAndReturn("Could not find $name", null)
    }
    mc.setDynamicPropertyValue = { String name, value ->
      def me = delegate
      def found

      found = delegate.hasLocalDynamicProperty(name)
      if(found) {
        return me.setLocalDynamicProperty(name, value)
      }

      found = delegateProps.find { prop ->
        return me."$prop"?.metaClass?.hasProperty(propVal, name) 
      }
      if(found) { 
        return delegate."$found" = value
      }

      found = delegateProps.findAll { me."$it"?.hasDynamicProperties() }?.find { prop ->
        return me."$prop"?.hasLocalDynamicProperty(name)
      }
      if(found) {
        return delegate."$found".setLocalDynamicProperty(name, value)
      }

      return delegate.addLocalDynamicProperty(name, value)
    }

    manglePropertyMissing(mc, delegateProps)
  }

  private static manglePropertyMissing(MetaClass mc, List delegateProps) {
    def(get,set) = getPropertyMissingPair(mc)
    mc.propertyMissing = { String name ->
      assert !(delegate instanceof Class)
      def me = delegate

      try {
        if(get) return get.invoke(me, name)
      } catch(MissingPropertyException mpe) { checkMPE(mpe, me.getClass(), name) }

      def toReturn = me.getDynamicPropertyValue(name)
      println "Returning $toReturn (${toReturn?.getClass()}) from propertyMissing(get)"
      return toReturn
    }
    mc.propertyMissing = { String name, value ->
      assert !(delegate instanceof Class)
      def me = delegate

      try {
        if(set) return set.invoke(me, name)
      } catch(MissingPropertyException mpe) { checkMPE(mpe, me.getClass(), name) }

      def toReturn = me.setDynamicPropertyValue(name, value)
      println "Returning $toReturn (${toReturn?.getClass()}) from propertyMissing(set)"
      return toReturn
    }
  }

  private static checkMPE(MissingPropertyException mpe, Class type, String propertyName) {
    if(mpe.type == type && mpe.property == propertyName) {
        // Ignore 
    } else { // Someone else blew up
      throw deepSanitize(mpe)
    }
  }

  private static List getPropertyMissingPair(MetaClass target) {
    def methods = target.methods
    def originalGetProp = methods.find { 
      !it.isStatic() && it.name == "propertyMissing" && it.nativeParameterTypes == ([String] as Class[]) 
    }   
    def originalSetProp = methods.find { 
      !it.isStatic() && it.name == "propertyMissing" && it.nativeParameterTypes == ([String, Object] as Class[]) 
    }   
    return [originalGetProp, originalSetProp]
  }   


}
