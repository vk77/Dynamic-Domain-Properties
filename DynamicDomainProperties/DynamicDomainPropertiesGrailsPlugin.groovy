import static org.codehaus.groovy.grails.commons.GrailsClassUtils.*
import static com.smokejumperit.grails.dynamicDomain.helper.DynamicDomainMethods.applyEvents
import static com.smokejumperit.grails.dynamicDomain.helper.DynamicDomainMethods.applyMethodsTo
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.stringify
import static com.smokejumperit.grails.dynamicDomain.helper.StringConverter.destringify
import static com.smokejumperit.grails.dynamicDomain.helper.MethodWrapper.wrapInstanceNoParmMethod

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

      application.domainClasses*.clazz.findAll { Class clazz ->
        println "Checking $clazz for 'dynamicProperties'"
        def dynProps = getStaticPropertyValue(clazz, "dynamicProperties")
        if(dynProps instanceof List) return true
        return dynProps
      }.each { Class clazz -> 
        println "Applying dynamic property methods to $clazz"
        applyEvents(clazz)
        applyMethodsTo(clazz) 
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
