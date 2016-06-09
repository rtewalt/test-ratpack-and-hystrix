import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandProperties
import com.netflix.hystrix.HystrixObservableCommand
import groovy.util.logging.Slf4j
import ratpack.exec.Blocking
import ratpack.rx.RxRatpack
import ratpack.test.exec.ExecHarness
import rx.Observable
import spock.lang.Specification

@Slf4j
class HystrixTimeoutSpec extends Specification {
  String getPassValue() {
    sleep(10000)
    'pass'
  }

  void 'tryTimeout - ratpack'() {
    when:
    long start = System.currentTimeMillis()
    def actual = runExecutorTest(true)
    long end = System.currentTimeMillis()

    then:
    actual == 'fail'

    end - start < 8000
  }

  void 'tryTimeout - vanilla rxjava'() {
    when:
    long start = System.currentTimeMillis()
    def actual = runExecutorTest(false)
    long end = System.currentTimeMillis()

    then:
    actual == 'fail'

    end - start < 8000
  }

  String runExecutorTest(boolean useRatpack) {
    RxRatpack.initialize()

    ExecHarness.yieldSingle {
      new HystrixObservableCommand<String>(
          HystrixObservableCommand.Setter.withGroupKey(
              HystrixCommandGroupKey.Factory.asKey('some-command-group')
          )) {

        @Override
        protected Observable<String> construct() {

          if(useRatpack) {
            Blocking.get {
              getPassValue()
            }.observe()
          } else {
            rx.Observable.just('the thing').forkEach().map {
              getPassValue()
            }
          }
        }

        @Override
        protected Observable<String> resumeWithFallback() {
          log.error('bad', getExecutionException())
          Observable.just('fail')
        }
      }.toObservable().doOnEach{
        println(it)
      }.doOnRequest{
        println(it)
      }.doOnError{
        println(it)
      }.promiseSingle()

    }.value
  }

}
