import klite.HttpExchange
import klite.annotations.GET
import klite.annotations.Path
import klite.annotations.QueryParam
import klite.i18n.Lang
import kotlinx.coroutines.delay

@Path("/hello")
class Routes {
  @GET
  fun sayHello() = SomeResponse("Hello")

  @GET("2")
  fun withExchange(exchange: HttpExchange) = "Hello2 ${exchange.method} ${exchange.path}"

  @GET("3")
  fun HttpExchange.asContext() = "${Lang.translate(this, "hello")} $method $path"

  @GET("/suspend")
  suspend fun suspend(exchange: HttpExchange): String {
    delay(100)
    return "Suspend ${exchange.method} ${exchange.path}"
  }

  @GET("/suspend204")
  suspend fun suspendNoContent() {
    delay(50)
  }

  @GET("/null")
  fun returnNull() = null

  @GET("/admin") @AdminOnly
  fun onlyForAdmins() = "Only for admins"

  @GET("/params")
  fun withOptionalParams(@QueryParam optional: Boolean = false, @QueryParam required: String, @QueryParam nullable: String?) = "$optional,$required,$nullable"

  @GET("/broken-render")
  fun brokenRender() = object { lateinit var hello: String }
}

data class SomeResponse(val hello: String, val world: Double = Math.PI)
