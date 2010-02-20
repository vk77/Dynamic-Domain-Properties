package com.smokejumperit.grails.dynamicDomain.helper

import static org.apache.log4j.Logger.getLogger

class MethodWrapper {

  static log = getLogger(this)

  static wrapInstanceNoParmMethod(target, String methodName, Closure toDo) {
    def metaClass = target instanceof MetaClass ? target : target.metaClass
    def methods = metaClass.methods
    def emptyClassArray = [] as Class[]
    def original = methods.find {
      !it.isStatic() && it.name == methodName && it.nativeParameterTypes == emptyClassArray
    }
    log.debug("Wrapping $methodName around $target")
    metaClass."$methodName" = {->
      assert !(delegate instanceof Class)
      log.trace("Executing $methodName wrapping $delegate")
      if(original) original.invoke(delegate)
      def doMe = toDo.clone()
      doMe.delegate = delegate
      doMe()
    }
  }

}
