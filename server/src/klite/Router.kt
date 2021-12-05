package klite

import klite.RequestMethod.*

class Router(
  val prefix: String,
  private val regexer: PathParamRegexer,
  decorators: List<Decorator>,
  bodyRenderers: List<BodyRenderer>,
  bodyParsers: List<BodyParser>
) {
  private val logger = logger()
  private val routes = mutableListOf<Route>()
  private val decorators = decorators.toMutableList()
  internal val bodyRenderers = bodyRenderers.toMutableList()
  internal val bodyParsers = bodyParsers.toMutableList()

  internal fun route(exchange: HttpExchange): Handler? {
    val suffix = exchange.path.removePrefix(prefix)
    return match(exchange.method, suffix)?.let { m ->
      exchange.pathParams = m.second.groups
      m.first
    }
  }

  private fun match(method: RequestMethod, path: String): Pair<Handler, MatchResult>? {
    for (route in routes) {
      if (method != route.method) continue
      route.path.matchEntire(path)?.let { return route.handler to it }
    }
    return null
  }

  fun add(route: Route) {
    routes += route.copy(handler = decorators.wrap(route.handler)).apply { logger.info("$method $prefix$path") }
  }

  fun get(path: Regex, handler: Handler) = add(Route(GET, path, handler))
  fun get(path: String = "", handler: Handler) = get(regexer.from(path), handler)

  fun post(path: Regex, handler: Handler) = add(Route(POST, path, handler))
  fun post(path: String = "", handler: Handler) = post(regexer.from(path), handler)

  fun put(path: Regex, handler: Handler) = add(Route(PUT, path, handler))
  fun put(path: String = "", handler: Handler) = put(regexer.from(path), handler)

  fun delete(path: Regex, handler: Handler) = add(Route(DELETE, path, handler))
  fun delete(path: String = "", handler: Handler) = delete(regexer.from(path), handler)

  fun decorator(decorator: Decorator) { decorators += decorator }
  fun before(before: Before) = decorator(before.toDecorator())
  fun after(after: After) = decorator(after.toDecorator())

  fun renderer(renderer: BodyRenderer) = bodyRenderers.add(0, renderer)
  fun parser(parser: BodyParser) = bodyParsers.add(0, parser)
}

enum class RequestMethod {
  GET, POST, PUT, DELETE, OPTIONS, HEAD
}

data class Route(val method: RequestMethod, val path: Regex, val handler: Handler)

/** Converts parameterized paths like "/hello/:world/" to Regex with named parameters */
open class PathParamRegexer(private val paramConverter: Regex = "/:([^/]+)".toRegex()) {
  open fun from(path: String) = paramConverter.replace(path, "/(?<$1>[^/]+)").toRegex()
}
