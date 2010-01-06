package com.smokejumperit.grails.dynamicDomain

class DynamicProperty {
  
  Class parentClass, propertyClass
  Long parentId
  String propertyName, propertyValue

  static constraints = {
    propertyName(unique:['parentClass', 'parentId'])
    propertyValue(nullable:true)
    propertyClass(nullable:true)
  }
 
}
