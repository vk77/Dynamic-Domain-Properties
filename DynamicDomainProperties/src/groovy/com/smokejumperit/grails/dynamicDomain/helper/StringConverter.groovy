package com.smokejumperit.grails.dynamicDomain.helper

class StringConverter {

  static getDomainHandler() { ApplicationProvider.application }

  static String stringify(value) {
    Class clazz = value?.getClass()
    switch(clazz) {
      case null: return null
      case Class:  return "${value.name}"
      case Boolean: case Character: case Double: case Float: case GString:
      case String: case Integer: case Long: case Short: case { clazz.isPrimitive() }:  
        return value.toString()
      case { ApplicationProvider.domainHandler?.isArtefact(clazz) }:
        return "${stringify(value.id)}"
      case { clazz.metaClass.getStaticMetaMethod("fromString") }: return value.toString()
      default: throw new Exception("Don't know how to stringify ${value} (${value.getClass()}): provide a static 'fromString(String)' method")
    }  
  }

  static destringify(Class clazz, String value) {
    if(value == null) return null
    switch(clazz) {
      case null: return null
      case String: case GString: return value
      case Boolean.TYPE: case Boolean: return Boolean.parseBoolean(value)
      case Character.TYPE: case Character: return value.charAt(0)
      case Class: return findClass(value)
      case Double.TYPE: case Double: return Double.parseDouble(value)
      case Float.TYPE: case Float: return Float.parseFloat(value)
      case Integer.TYPE: case Integer: return Integer.parseInt(value)
      case Long.TYPE: case Long: return Long.parseLong(value)
      case Short.TYPE: case Short: return Short.parseShort(value)
      case { ApplicationProvider.domainHandler?.isArtefact(clazz) }:
        def id = destringify(clazz.metaClass.getMetaProperty("id").type, value)
        return clazz.get(id)
      case { clazz.metaClass.getStaticMetaMethod("fromString") }:
        return clazz.fromString(value)
      default: throw new Exception("Don't know how to destringify $value (${value.getClass()})")
    }   
  }   

  static Class findClass(String clazzName) {
    def impls = []

    def app = ApplicationProvider.application
    if(app) {
      impls << { app.getClassForName(clazzName) }
      impls << { Class.forName(clazzName, true, app.classLoader) }
    }
    impls << { Class.forName(clazzName, true, StringConverter.getClassLoader()) }

    return impls.inject(null) { Class memo, Closure impl ->
      try {
        return memo ?: impl()
      } catch(ClassNotFoundException skip) {}
    }
  }
  
}
