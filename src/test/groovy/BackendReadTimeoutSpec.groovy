import com.google.common.net.HttpHeaders
import org.mockserver.client.server.MockServerClient
import org.mockserver.matchers.Times
import ratpack.groovy.test.GroovyRatpackMainApplicationUnderTest
import ratpack.http.MediaType
import ratpack.http.client.ReceivedResponse
import ratpack.test.ApplicationUnderTest
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class BackendReadTimeoutSpec extends Specification {

  public static final int BACKEND_RESPONSE_DELAY = 20000
  public static final int HAPPY_RESPONSE_TIME_UPPER_BOUND = 15000

  MockServerClient backendMockAdmin

  @AutoCleanup
  ApplicationUnderTest aut = new GroovyRatpackMainApplicationUnderTest()

  void setup() {
    /**
     * Assumes "docker run -p 1080:1080 jamesdbloom/mockserver" is running locally.  If using docker-machine, you may
     * need to substitute localhost for the docker-machine ip.
     */
    backendMockAdmin = new MockServerClient('localhost', 1080)

    System.setProperty('some.api.backend.url', 'http://localhost:1080/backend_call')
    System.setProperty('some.api.backend.fallback', 'fail')
  }

  @Unroll
  void 'get site readiness is false for timeout'() {
    given:
    backendMockAdmin.when(
        org.mockserver.model.HttpRequest.request()
            .withMethod("GET")
            .withPath("/backend_call"),
        Times.exactly(1)
    ).respond(
        org.mockserver.model.HttpResponse.response().withConnectionOptions()
            .withDelay(TimeUnit.MILLISECONDS, BACKEND_RESPONSE_DELAY)
            .withBody("pass")
            .withStatusCode(200)
    )

    when:
    long start = System.currentTimeMillis()
    ReceivedResponse response = aut.httpClient.get('/will_call_backend')
    long end = System.currentTimeMillis()

    String responseText = response.body.text
    String innerResponseTime = response.headers.get("X-Response-Time")

    then:
    responseText == 'fail'
    innerResponseTime.toDouble() < HAPPY_RESPONSE_TIME_UPPER_BOUND
    end - start < HAPPY_RESPONSE_TIME_UPPER_BOUND // a buffer large enough that 1 sec timeout + normal processing won't be greater, but is less than the delay abpve

  }
}
