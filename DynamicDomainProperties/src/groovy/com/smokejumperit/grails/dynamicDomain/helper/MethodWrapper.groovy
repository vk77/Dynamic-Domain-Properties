package com.smokejumperit.grails.dynamicDomain.helper

class MethodWrapper {

  static wrapInstanceNoParmMethod(target, String methodName, Closure toDo) {
    def metaClass = target instanceof MetaClass ? target : target.metaClass
    def methods = metaClass.methods
    def emptyClassArray = [] as Class[]
    def original = methods.find {
      !it.isStatic() && it.name == methodName && it.nativeParameterTypes == emptyClassArray
    }
    println "Wrapping $methodName around $target"
    metaClass."$methodName" = {->
      assert !(delegate instanceof Class)
      println "Executing $methodName wrapping $delegate"
      if(original) original.invoke(delegate)
      def doMe = toDo.clone()
      doMe.delegate = delegate
      doMe()
    }
  }

}
