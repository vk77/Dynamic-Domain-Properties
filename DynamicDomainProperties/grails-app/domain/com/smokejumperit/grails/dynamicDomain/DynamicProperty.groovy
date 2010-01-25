package com.smokejumperit.grails.dynamicDomain

import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.stringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.destringify

class DynamicProperty {

  static transients = ['parent', 'parentLocal', 'propertyValue', 'propertyValueLocal']
  static belongsTo = [propertyParent:DynamicProperty]
  
  Class parentClass, propertyClass
  long parentId
  String propertyName, propertyValueString
  int idx = -1
  boolean isKey = false
  def parentLocal, propertyValueLocal

  static constraints = {
    propertyName(unique:['parentClass', 'parentId'])
    propertyValueString(nullable:true, column:'property_value', maxSize:Integer.MAX_VALUE)
    propertyClass(nullable:true)
    idx(unique:['propertyName', 'isKey', 'parentClass', 'parentId'], min:-1)
  }

  def beforeInsert() {
    beforeUpdate()
  }

  def beforeUpdate() {
    if(propertyValueString == null || propertyValueClass == null) {
      propertyValueClass = propertyValueLocal?.getClass()
      propertyValueString = stringify(propertyValueLocal)
    }
    if(parentClass == null || parentId == null) {
      if(!parentLocal?.id) parentLocal?.save(flush:true, failOnError:true)
      parentClass = parentLocal?.getClass() ?: parentClass
      parentId = stringify(parentLocal?.id) ?: parentId
    }
  }

  def afterInsert() {
    afterUpdate()
  }

  def afterUpdate() {
    persistChildren()
  }

  def getParent() {
    if(parentLocal == null) {
      parentLocal = parentClass?.get(destringify(parentId))
    }
    return parentLocal
  }

  def setParent(parent) {
    parentLocal = parent
    parentClass = null
    parentId = null
  }

  def getPropertyValue() {
    if(propertyValueLocal == null) {
      propertyValueLocal = destringify(propertyValueString)
    }
    return propertyValueLocal
  }

  def setPropertyValue(value) {
    propertyValueLocal = value
    propertyValueString = null
    propertyClass = value?.getClass()
  }

  static findAllByParent(parent) {
    if(parent == null) return []
    return findAll("""
      from ${getName()} foo 
      where foo.parentClass = :parentClass
        and foo.parentId = :parentId
        and foo.idx = -1
    """, [parentClass:parent.getClass(), parentId:stringify(parent.id)])
  }

  void persistChildren() {
    withTransaction {
      persistChildrenImpl()
    }
  }

  void persistChildrenImpl() {
    def toPersist = []

    executeUpdate("""
      delete ${this.getClass().name} foo 
      where foo.propertyParent = :parent
    """, [parent:this])

    def me = this
    def value = propertyValue
    if(value instanceof Collection) {
      value.eachWithIndex { val, idx ->
        def newb = new DynamicProperty() 
        newb.parent = me
        newb.propertyValue = val
        newb.propertyName = me.propertyName
        newb.idx = idx
        toPersist << newb
      }
      propertyValueString = stringify(value.size())
    } else if(value instanceof Map) {
      def idx = 0
      value.each { k,v ->
        [true:k,false:v].each { isKey, val ->
          def newb = new DynamicProperty()
          newb.idx = idx
          newb.parent = me
          newb.propertyValue = val
          newb.propertyName = me.propertyName
          newb.isKey = isKey
          toPersist << newb
        }
        idx += 1
      }
      propertyValueString = stringify(value.size())
    } else {
      propertyValueString = stringify(value)
    }
    propertyClass = value.getClass()

    toPersist*.persist()
   }

}
