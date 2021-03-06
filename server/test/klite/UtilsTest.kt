package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class UtilsTest {
  @Test fun urlParams() {
    val params = mapOf("Hello" to "Wörld", "1" to "2", "null" to null)
    expect(urlEncodeParams(params)).toEqual("Hello=W%C3%B6rld&1=2")
    expect(urlDecodeParams("Hello=W%C3%B6rld&1=2")).toEqual(params - "null")
  }
}
