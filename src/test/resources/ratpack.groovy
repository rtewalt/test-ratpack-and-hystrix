import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixObservableCommand
import ratpack.exec.Execution
import ratpack.handling.ResponseTimer
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import rx.Observable
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

ratpack {
  bindings {
    add(ResponseTimer.decorator())
  }

  handlers {
    get('will_call_backend') {

      HttpClient httpClient = registry.get(HttpClient)


      HystrixObservableCommand observableCommand = new HystrixObservableCommand<String>(
          HystrixObservableCommand.Setter.
              withGroupKey(
                  HystrixCommandGroupKey.Factory.asKey('some-group')
              )
      ) {

        final Execution execution = Execution.current()

        @Override
        protected Observable<String> construct() {
          println("Observable Timeout: ${properties.executionTimeoutInMilliseconds().get()}ms")

          httpClient.get(URI.create(System.getProperty('some.api.backend.url'))).observe().
              map { ReceivedResponse receivedResponse ->
                receivedResponse.body.text
              }
        }

        @Override
        protected Observable<String> resumeWithFallback() {
          getExecutionException().printStackTrace()
          Observable.just(System.getProperty('some.api.backend.fallback')).observeOn(RxRatpack.scheduler(execution.controller))
        }
      }

      observableCommand.toObservable().subscribe { next ->
        render(next)
      }

    }
  }
}
