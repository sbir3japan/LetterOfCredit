package eloc.plugin

import eloc.api.ELOCApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

/**
 * This class defines the plugin for the ELOC application.
 */
class ELOCPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::ELOCApi))
    override val staticServeDirs = mapOf(
            // This will serve the loc directory in resources to /loc
            "loc" to javaClass.classLoader.getResource("loc").toExternalForm(),
            "loc/seller" to javaClass.classLoader.getResource("loc").toExternalForm(),
            "loc/buyer" to javaClass.classLoader.getResource("loc").toExternalForm(),
            "loc/advising" to javaClass.classLoader.getResource("loc").toExternalForm(),
            "loc/issuing" to javaClass.classLoader.getResource("loc").toExternalForm()
    )
}