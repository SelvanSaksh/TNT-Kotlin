package resolver

import kotlinx.serialization.json.JsonObject

/**
 * Extra fields merged into the `POST /productmaster/config` request.
 *
 * The web resolver loads these from `/config/config.json`, which is not
 * published alongside the app, so that request never fires there. The call is
 * ported and stays dormant until a payload is supplied here.
 */
object ResolverConfig {
    var payload: JsonObject? = null
}
