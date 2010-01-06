import static org.codehaus.groovy.grails.commons.GrailsClassUtils.*

import com.smokejumperit.grails.dynamicDomain.DynamicProperty as DynProp
import org.codehaus.groovy.grails.commons.*

class DynamicDomainPropertiesGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
      'conf', 'controllers', 'i18n', 'services', 'taglib',
      'utils', 'views'
    ].collect { "grails-app/$it/**" } + [
      "web-app/**",
      "test/**"
    ]

    // TODO Fill in these fields
    def author = "Robert Fischer"
    def authorEmail = "robert.fischer@smokejumperit.com"
    def title = "Provides a domain class with arbitrary properties"
    def description = '''\\

'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/dynamic-domain-properties"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = { }

    def doWithDynamicMethods = { ctx ->
      application.domainClasses*.clazz.each { Class clazz ->
        def dynProps = getStaticPropertyValue(clazz, "dynamicProperties")
        switch(dynProps) {
          case null: return
          case Boolean: case Boolean.TYPE:  
            if(!dynProps) return
            dynProps = []
            break
          case List: break
          default: 
            throw new Exception("Don't know how to deal with dynamicProperties of $dynProps (${dynProps?.getClass()})")
        }

        def(get,set) = getPropertyMissingPair(clazz)
        clazz.metaClass.propertyMissing = { String name ->
          try {
            if(get) return get.invoke(delegate, name)
          } catch(MissingPropertyException mpe) { checkMPE(mpe, delegate.getClass(), name) }
          def d = delegate
          def toRun = (dynProps - name).collect {
            d?.metaClass?.getProperty(d, it)
          }.find { it?.metaClass?.hasProperty(d, name) }
          if(toRun) return toRun.metaClass.getProperty(toRun, name)
          return queryProperty(delegate, name)
        }
        clazz.metaClass.propertyMissing = { String name, value ->
          try {
            if(set) return set.invoke(delegate, name, value)
          } catch(MissingPropertyException mpe) { checkMPE(mpe, delegate.getClass(), name) }
          def d = delegate
          def toRun = (dynProps - name).collect {
            d?.metaClass?.getProperty(d, it)
          }.find { it?.metaClass?.hasProperty(d, name) }
          if(toRun) return toRun.metaClass.setProperty(toRun, name, value)
          return assignProperty(delegate, name, value)
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

    static assignProperty(GrailsApplication application, parent, String name, value) {
      def args = [parentClass:parent.getClass(), parentId:parent.id, propertyName:name]
      def found = DynProp.findAllWhere(*args) ?: new DynProp(*args) 
      if(value instanceof Collection) {
        DynProp.executeUpdate("""
          delete ${DynProp.getClass()} foo 
          where foo.parentClass = :parentClass 
            and foo.parentId = :parentId 
            and foo.propertyName like concat(:propertyName, '[%')
        """, args)
        value.eachWithIndex { val, idx ->
          assignProperty(application, parent, "$name[$idx]", val)
        }
        found.propertyValue = value.size()
        found.propertyClass = value.getClass()
      } else if(value instanceof Map) {
        DynProp.executeUpdate("""
          delete ${DynProp.getClass()} foo 
          where foo.parentClass = :parentClass 
            and foo.parentId = :parentId 
            and (
                  foo.propertyName like concat(:propertyName, '[key:%')
              or  foo.propertyName like concat(:propertyName, '[value:%')
            )
        """, args)

        def ctr = 0
        value.each { k,v ->
          assignProperty(application, parent, "$name[key:$ctr]", k)
          assignProperty(application, parent, "$name[value:$ctr]", v)
          ctr += 1
        }
        found.propertyValue = value.size()
        found.propertyClass = value.getClass()
      } else {
        found.propertyValue = stringify(application, value);
        found.propertyClass = value?.getClass()
      }
      found.save()
      return value
    }

    static String stringify(GrailsApplication application, value) {
      Class clazz = value?.getClass()
      switch(value) {
        case null: return null
        case String: return value;
        case Class:  return "${value.name}"
        case Boolean: case Character: case Double: case Float:
        case Integer: case Long: case Short: case { clazz.isPrimitive() }: 
          return "${value}"
        case { application.getArtefactHandler("Domain").isArtefact(clazz) }:
          return "${stringify(application, value.id)}"
        case { clazz.metaClass.getStaticMetaMethod("fromString") }: return value.toString()
        default: throw new Exception("Don't know how to stringify $value (${value.getClass()})")
      }
    }

    static destringify(GrailsApplication application, Class clazz, String value) {
      if(value == null) return null
      switch(clazz) {
        case null: return null
        case String: return value
        case Boolean.TYPE: case Boolean: return Boolean.parseBoolean(value)
        case Character.TYPE: case Character: return value.charAt(0)
        case Class: return application.getClassForName(value)
        case Double.TYPE: case Double: return Double.parseDouble(value)
        case Float.TYPE: case Float: return Float.parseFloat(value)
        case Integer.TYPE: case Integer: return Integer.parseInt(value)
        case Long.TYPE: case Long: return Long.parseLong(value)
        case Short.TYPE: case Short: return Short.parseShort(value)
        case { application.getArtefactHandler("Domain").isArtefact(clazz) }:
          def id = destringify(application, clazz.metaClass.getMetaProperty("id").type, value)
          return clazz.get(id)
        case { clazz.metaClass.getStaticMetaMethod("fromString") }:
          return clazz.fromString(value)
        default: throw new Exception("Don't know how to destringify $value (${value.getClass()})")
      }
    }

    static queryProperty(GrailsApplication application, parent, String name) { 
      def found = DynProp.findAllWhere(parentClass:parent.getClass(), parentId:parent.id, propertyName:name)
      if(!found) return null
      if(Collection.isAssignableFrom(found.propertyClass)) {
        def fallback = ArrayList
        if(List.isAssignableFrom(found.propertyClass)) {
          fallback = ArrayList
        } else if(SortedSet.isAssignableFrom(found.propertyClass)) {
          fallback = TreeSet
        } else if(Set.isAssignableFrom(found.propertyClass)) {
          fallback = LinkedHashSet
        } 
        def collection = newInstance(found.propertyClass, fallback)
        def size = Long.parseLong(found.propertyValue)
        (0..<size).each { idx ->
          collection.add(queryProperty(application, parent, "$name[$idx]"))
        }
        return collection
      } else if(Map.isAssignableFrom(found.propertyClass)) {
        def map = newInstance(found.propertyClass, LinkedHashMap)
        def size = Long.parseLong(found.propertyValue)
        (0..<size).each { idx ->
          def k = queryProperty(application, parent, "$name[key:$idx]")
          def v = queryProperty(application, parent, "$name[value:$idx]")
          map[k] = v
        }
        return map
      } else {
        return destringify(application, found.propertyClass, found.propertyValue)
      }
    }

    static newInstance(Class main, Class fallback) {
      try {
        return main.newInstance()
      } catch(Exception e) {
        return fallback.newInstance()
      }
    }

    private static List getPropertyMissingPair(Class clazz) {
      def methods = clazz.metaClass.methods
      def originalGetProp = methods.find { 
        it.name == "propertyMissing" && it.nativeParameterTypes == ([String] as Class[]) 
      }
      def originalSetProp = methods.find { 
        it.name == "propertyMissing" && it.nativeParameterTypes == ([String, Object] as Class[]) 
      }
      return [originalGetProp, originalSetProp]
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}
