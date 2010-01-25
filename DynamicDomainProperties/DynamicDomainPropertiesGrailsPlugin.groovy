import static org.codehaus.groovy.grails.commons.GrailsClassUtils.*

import com.smokejumperit.grails.dynamicDomain.DynamicProperty as DynProp
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.runtime.metaclass.*

class DynamicDomainPropertiesGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [
      gormLabs:"0.8.3 > *"
    ]
    // resources that are excluded from plugin packaging
    def pluginExcludes =[
      'conf', 'controllers', 'i18n', 'services', 'taglib',
      'utils', 'views'
    ].collect { "grails-app/$it/**" } + [
      "web-app/**",
      "test/**",
      "**/com/smokejumperit/test/**"
    ]

    // TODO Fill in these fields
    def author = "Robert Fischer"
    def authorEmail = "robert.fischer@smokejumperit.com"
    def title = "Dynamic Domain Properties"
    def description = '''\\
Allows a domain class to have dynamic persistent properties.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/dynamic-domain-properties"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = { }

    def doWithDynamicMethods = { ctx ->
      load(application)
    }

    private static void load(GrailsApplication application) {
      Object.metaClass.hasDynamicProperties = {-> false }

      application.domainClasses*.clazz.each { Class clazz ->

        def dynProps = getStaticPropertyValue(clazz, "dynamicProperties")
        switch(dynProps) {
          case null: return
          case Boolean: case Boolean.TYPE:  
            if(!dynProps) return  // static dynamicProperties = false
            dynProps = []         // static dynamicProperties = true 
            break
          case List: break
          default: 
            throw new Exception("Don't know how to deal with a 'dynamicProperties' properties with value '$dynProps' (${dynProps?.getClass()})")
        }

        def mc = clazz.metaClass

        mc.hasDynamicProperties = {-> true }

        mc.localDynamicProperties = [:]

        afterMethod(clazz, "onLoad") {->
          def localDynProps = new HashMap()
          DynProp.findAllByParent(delegate)?.each {
            localDynProps[it.propertyName] = it
          }
          delegate.localDynamicProperties = localDynProps
        }

        mc.addLocalDynamicProperty = { DynProp prop ->
          localDynProps[it.propertyName] = it
        }
  
        mc.addLocalDynamicProperty = { String name, value ->
          def dynProp = new DynProp()
          dynProp.parent = delegate
          dynProp.propertyName = name
          dynProp.propertyValue = value
          delegate.addLocalDynamicProperty(dynProp)
        }

        mc.getLocalDynamicPropertiesMap = {-> 
          def toReturn = [:]
          localDynamicProperties.values().each { v -> toReturn[v.propertyName] = v.propertyValue }
          return Collections.unmodifiableMap(toReturn)
        }

        mc.getLocalDynamicProperty = { String name -> localDynamicProperties[name] }
        mc.getLocalDynamicPropertyValue = { String name -> getLocalDynamicProperty(name)?.propertyValue }
        mc.hasLocalDynamicProperty = { String name -> localDynamicProperties.containsKey(name) }

        def saveImpl = {-> localDynamicProperties.values()*.persist() }
        ['afterInsert', 'afterUpdate'].each { afterMethod(clazz, it, saveImpl) } 

        def(get,set) = getPropertyMissingPair(clazz)
        mc.propertyMissing = { String name ->
          assert !(delegate instanceof Class)
          def me = delegate

          try {
            if(get) return get.invoke(me, name)
          } catch(MissingPropertyException mpe) { checkMPE(mpe, me.getClass(), name) }

          if(me.hasLocalDynamicProperty(name)) {
            return me.getLocalDynamicPropertyValue(name)
          }

          def props = (dynProps - name).collect { mc.getProperty(me, it) }.flatten()

          def toRun = props.find { it?.metaClass?.hasProperty(it, name) }
          if(toRun) return toRun."$name"
        
          toRun = props.find { 
            it?.hasDynamicProperties() && it?.hasLocalDynamicProperty(name) 
          }
          if(toRun) return toRun.getLocalDynamicPropertyValue(name)

          throw new MissingPropertyException("No explicit or dynamic property found", name, me.getClass())
        }
        mc.propertyMissing = { String name, value ->
          assert !(delegate instanceof Class)
          try {
            if(set) return set.invoke(delegate, name, value)
          } catch(MissingPropertyException mpe) { checkMPE(mpe, delegate.getClass(), name) }
          if(delegate.hasLocalDynamicProperty(name)) {
            delegate.getLocalDynamicProperty(name).propertyValue = value
          } else {
            delegate.addLocalDynamicProperty(name, value)
          }
        }
      }
    }

    private static checkMPE(MissingPropertyException mpe, Class type, String propertyName) {
      if(mpe.type == type && mpe.property == propertyName) { 
        // Ignore 
      } else { // Someone else blew up
        throw mpe
      }
    }

    static newInstance(Class main, Class fallback) {
      try {
        return main.newInstance()
      } catch(Exception e) {
        return fallback.newInstance()
      }
    }

    private static List getPropertyMissingPair(target) {
      def methods = target.metaClass.methods
      def originalGetProp = methods.find { 
        !it.isStatic() && it.name == "propertyMissing" && it.nativeParameterTypes == ([String] as Class[]) 
      }
      def originalSetProp = methods.find { 
        !it.isStatic() && it.name == "propertyMissing" && it.nativeParameterTypes == ([String, Object] as Class[]) 
      }
      return [originalGetProp, originalSetProp]
    }

    private static void afterMethod(target, String methodName, Closure toDo) {
      def methods = target.metaClass.methods
      def original = methods.find {
        !it.isStatic() && it.name == methodName && it.nativeParameterTypes == ([] as Class[]) 
      }
      target.metaClass."$methodName" = {->
        assert !(delegate instanceof Class)
        if(original) original.invoke(delegate)
        def doMe = toDo.clone()
        doMe.delegate = delegate
        doMe()
      }
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
      load(event.application)
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
      load(event.application)
    }
}
