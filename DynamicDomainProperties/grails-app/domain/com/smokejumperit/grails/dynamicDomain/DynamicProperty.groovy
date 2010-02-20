package com.smokejumperit.grails.dynamicDomain

import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.stringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.destringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.findClass
import static grails.util.GrailsUtil.deepSanitize
import static org.apache.commons.lang.ObjectUtils.identityToString

class DynamicProperty {

  static final transients = ['parent', 'parentLocal', 'propertyValue', 'propertyValueLocal', 'children']
  
  String parentClassValue, propertyClassValue
  String parentId, propertyName, propertyValueString
  int idx = -1
  boolean isKey = false
  def parentLocal, propertyValueLocal, children

  /*static final parentValidator = { val, obj -> 
    return (val ?: obj.parentLocal != null)
  }*/

  static final constraints = {
    propertyName(unique:['parentClassValue', 'parentId', 'idx', 'isKey'])
    propertyValueString(nullable:true, column:'property_value')
    propertyClassValue(nullable:true, column:"property_class")
    parentId(nullable:true/*, validator:parentValidator.clone()*/)
    parentClassValue(nullable:true/*, validator:parentValidator.clone()*/, column:"parent_class")
    idx(unique:['propertyName', 'isKey', 'parentClassValue', 'parentId'], min:-1)
  }

  def beforeInsert() {
    if(propertyValueString == null || propertyClassValue == null) {
      propertyClassValue = propertyValueLocal?.getClass()?.name
      propertyValueString = makeValueString(propertyValueLocal)
    }
    if(parentClassValue == null || parentId == null) {
      parentClassValue = parentLocal?.getClass()?.name
      parentId = stringify(parentLocal?.id) 
    }
  }

  def beforeUpdate() { beforeInsert() }

  def afterInsert() {
    persistChildren()
  }

  def afterUpdate() { afterInsert() }

  static String makeValueString(from) {
    switch(from?.getClass()) {
      case Collection: case Map: return "${from.size()}"
      default: return stringify(from)
    }
  }

  def getParent() {
    if(!parentLocal && parentClassValue && parentId) { 
      Class parentClass = findClass(parentClassValue)
      def idProp = parentClass.metaClass.getProperties().find { it.name == "id" }
      assert idProp
      def idType = idProp.type
      assert idType
      parentLocal = parentClass?.get(destringify(idType, parentId))
    }
    return parentLocal
  }

  def setParent(parent) {
    if(this == parent) throw new IllegalArgumentException("Cannot be my own parent")
    parentLocal = parent
    parentClassValue = null
    parentId = null
  }

  def getPropertyValue() {
    if(propertyValueLocal == null) {
      propertyValueLocal = makePropertyValue(this)
    }
    return propertyValueLocal
  }

  static makePropertyValue(DynamicProperty dynProp) {
    def clazz = findClass(dynProp.getPropertyClassValue())
    def val = dynProp.getPropertyValueString()
    switch(clazz) {
      case null: return null
      case Collection:
        def dbValues = query("parentId = :parId and parentClassValue = :parClazz",
          [parId:stringify(dynProp.id), parClazz:dynProp.getClass().name]
        ) as List
        def toReturn = constructWithFallback(clazz, [])
        (0..Long.parseLong(val)-1).each { idx ->
          toReturn[idx] = dbValues.find { it.idx == idx }?.propertyValue
        }
        return toReturn
      case Map:
        def dbValues = query("parentId = :parId and parentClassValue = :parClazz",
          [parId:stringify(dynProp.id), parClazz:dynProp.getClass().name]
        ) as List
        def toReturn = constructWithFallback(clazz, [:])
        (0..Long.parseLong(val)-1).each { idx ->
          def k = dbValues.find { it.idx == idx && it.isKey == true }?.propertyValue
          def v = dbValues.find { it.idx == idx && it.isKey == false }?.propertyValue
          toReturn[k] = v
        }
        return toReturn
      default:
        return destringify(clazz, val)
    }
  }

  static constructWithFallback(Class clazz, fallback) {
    try {
      return clazz.newInstance()
    } catch(Exception e) {
      new DynamicProperty().log.warn("Could not instantiate $clazz", e)
      return fallback
    }
  }

  def setPropertyValue(value) {
    propertyValueLocal = value
    propertyValueString = null
    propertyClassValue = null
  }

  static findAllByParent(parent) {
    if(!parent?.id) return []
    return createCriteria().list {
      and {
        eq('parentClassValue', parent.getClass().name)
        eq('parentId', stringify(parent.id))
        eq('isKey', false)
        eq('idx', -1)
      }
    }
  }

  void persistChildren() {
    withNewSession { session ->
      withTransaction {
        persistChildrenImpl()
      }
      session.flush()
    }
  }

  void persistChildrenImpl() {
    def me = this
    if(!me.id) throw new IllegalStateException("Can only persist children if parent is persisted")
    def children = findAllByParent(me)
    def toPersist = []

    def value = propertyValue
    if(value instanceof Collection) {
      value.eachWithIndex { val, idx ->
        def newb = children.find { 
          it.idx == idx
        } ?: new DynamicProperty(idx:idx, parent:me)
        newb.propertyValue = val
        newb.propertyName = me.propertyName
        toPersist << newb
      }
      propertyValueString = stringify(value.size())
    } else if(value instanceof Map) {
      def idx = 0
      value.each { k,v ->
        [true:k,false:v].each { isKey, val ->
          def newb = children.find { 
            it.isKey == isKey &&
            it.idx == idx
          } ?: new DynamicProperty(isKey:isKey, idx:idx, parent:me)
          newb.propertyValue = val
          newb.propertyName = me.propertyName
          toPersist << newb
        }
        idx += 1
      }
      propertyValueString = stringify(value.size())
    } else {
      propertyValueString = stringify(value)
    }
    propertyClassValue = value.getClass().name

    children.removeAll(toPersist)
    children.each {
      log.debug("Deleting child $it: ${it.properties}")
      it.delete(flush:true)
    }
    toPersist.each {
      log.debug("Persisting child $it: ${it.properties}")
      it.save(failOnError:true)
    }
   }

}
