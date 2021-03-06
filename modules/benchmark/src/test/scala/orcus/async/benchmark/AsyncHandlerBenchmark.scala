package orcus.async
package benchmark

import java.util.concurrent._
import java.util.function.Supplier

import cats.{Applicative, Traverse}
import cats.instances.vector._
import org.openjdk.jmh.annotations._
import com.twitter.util.{Await => TAwait, Future => TFuture}
import _root_.monix.execution.Scheduler
import _root_.monix.eval.Task
import cats.effect.{ContextShift, IO}

import scala.concurrent.{ExecutionContext, Await => SAwait, Future => SFuture}
import scala.util.Random

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Fork(
  value = 2,
  jvmArgs = Array(
    "-server",
    "-Xms2g",
    "-Xmx2g",
    "-XX:NewSize=1g",
    "-XX:MaxNewSize=1g",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:+UseParallelGC",
    "-XX:-UseBiasedLocking",
    "-XX:+AlwaysPreTouch"
  )
)
abstract class AsyncHandlerBenchmark {

  final val Xs: Vector[Int] = Vector.range(1, 50)
  final val Rnd: Random     = new Random

  @Param(Array("1", "2", "4", "8", "16", "32", "64", "0"))
  var threads: Int = _

  var backgroundService: ExecutorService = _

  def daemonThreadFactory: ThreadFactory = new ThreadFactory {
    def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setDaemon(true)
      if (t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      t
    }
  }

  @Setup
  def setup(): Unit =
    if (threads <= 0)
      backgroundService = Executors.newCachedThreadPool(daemonThreadFactory)
    else
      backgroundService = Executors.newFixedThreadPool(threads, daemonThreadFactory)

  @inline final def compute(i: Int): CompletableFuture[Int] =
    CompletableFuture.supplyAsync(new Supplier[Int] {
      def get(): Int = Rnd.nextInt(i) / i
    }, backgroundService)

  @TearDown
  def tearDown(): Unit = {
    backgroundService.shutdown()
    if (!backgroundService.awaitTermination(10, TimeUnit.SECONDS)) {
      val _ = backgroundService.shutdownNow()
    }
  }
}

class CatsAsyncHandler extends AsyncHandlerBenchmark {
  import orcus.async.catsEffect.concurrent._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newWorkStealingPool())

  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)

  @Benchmark
  def bench: Vector[Int] = {
    val p = implicitly[Par[IO]]
    val f = Traverse[Vector].traverse[IO, Int, Int](Xs)(i => p.parallel(compute(i)))
    SAwait.result(f.unsafeToFuture(), 10.seconds)
  }
}

class MonixAsyncHandler extends AsyncHandlerBenchmark {
  import orcus.async.monix._
  import scala.concurrent.duration._

  implicit val scheduler: Scheduler = Scheduler.computation()

  @Benchmark
  def bench: Vector[Int] = {
    val p = implicitly[Par[Task]]
    val f = Traverse[Vector].traverse[Task, Int, Int](Xs)(i => p.parallel(compute(i)))
    SAwait.result(f.runToFuture, 10.seconds)
  }
}

class ScalaAsyncHandler extends AsyncHandlerBenchmark {
  import cats.instances.future._
  import orcus.async.future._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newWorkStealingPool())

  @Benchmark
  def bench: Vector[Int] = {
    val p = implicitly[Par[SFuture]]
    val f = Traverse[Vector].traverse[SFuture, Int, Int](Xs)(i => p.parallel(compute(i)))
    SAwait.result(f, 10.seconds)
  }
}

class ScalaJavaConverter extends AsyncHandlerBenchmark {
  import cats.instances.future._
  import scala.concurrent.duration._
  import scala.compat.java8.FutureConverters._

  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newWorkStealingPool())

  @Benchmark
  def bench: Vector[Int] = {
    val f = Traverse[Vector].traverse[SFuture, Int, Int](Xs)(i => compute(i).toScala)
    SAwait.result(f, 10.seconds)
  }
}

class TwitterAsyncHandler extends AsyncHandlerBenchmark {
  import com.twitter.conversions.DurationOps._
  import io.catbird.util._
  import orcus.async.twitterUtil.future._

  @Benchmark
  def bench: Vector[Int] = {
    val p = implicitly[Par[TFuture]]
    val f = Traverse[Vector].traverse[TFuture, Int, Int](Xs)(i => p.parallel(compute(i)))
    TAwait.result(f, 10.seconds)
  }
}

class ArrowsTwitterAsyncHandler extends AsyncHandlerBenchmark {
  import arrows.twitter.Task
  import com.twitter.conversions.DurationOps._
  import orcus.async.arrowsTwitter.future._

  implicit val applicativeTask: Applicative[Task] = new Applicative[Task] {
    def pure[A](x: A): Task[A] = Task.value(x)

    def ap[A, B](ff: Task[A => B])(fa: Task[A]): Task[B] = ff.flatMap(fa.map)
  }

  @Benchmark
  def bench: Vector[Int] = {
    val p = implicitly[Par[Task]]
    val f = Traverse[Vector].traverse[Task, Int, Int](Xs)(i => p.parallel(compute(i))).run
    TAwait.result(f, 10.seconds)
  }
}
