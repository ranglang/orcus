package orcus

import cats.{Applicative, ApplicativeError}
import cats.data.Kleisli
import orcus.async.Par
import orcus.internal.ScalaVersionSpecifics._
import org.apache.hadoop.conf.{Configuration => HConfig}
import org.apache.hadoop.hbase.client.{
  AsyncTable,
  RowMutations,
  ScanResultConsumerBase,
  Append => HAppend,
  Delete => HDelete,
  Get => HGet,
  Increment => HIncrement,
  Put => HPut,
  Result => HResult,
  ResultScanner => HResultScanner,
  Row => HRow,
  Scan => HScan
}
import org.apache.hadoop.hbase.{TableName => HTableName}

import scala.collection.JavaConverters._

object table {

  type AsyncTableT = AsyncTable[T] forSome { type T <: ScanResultConsumerBase }

  def getName[F[_]](t: AsyncTableT)(
      implicit
      F: Applicative[F]
  ): F[HTableName] =
    F.pure(t.getName)

  def getConfiguration[F[_]](t: AsyncTableT)(
      implicit
      F: Applicative[F]
  ): F[HConfig] =
    F.pure(t.getConfiguration)

  def exists[F[_]](t: AsyncTableT, get: HGet)(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F]
  ): F[Boolean] =
    FE.map(F.parallel(t.exists(get)))(_.booleanValue())

  def get[F[_]](t: AsyncTableT, a: HGet)(
      implicit
      F: Par[F]
  ): F[HResult] =
    F.parallel(t.get(a))

  def put[F[_]](t: AsyncTableT, a: HPut)(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F]
  ): F[Unit] =
    FE.map(F.parallel(t.put(a)))(_ => ())

  def scanAll[F[_]](t: AsyncTableT, a: HScan)(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F]
  ): F[Seq[HResult]] =
    FE.map(F.parallel(t.scanAll(a)))(_.asScala.toSeq)

  def getScanner[F[_]](t: AsyncTableT, a: HScan)(
      implicit
      FE: ApplicativeError[F, Throwable]
  ): F[HResultScanner] =
    FE.catchNonFatal(t.getScanner(a))

  def delete[F[_]](t: AsyncTableT, a: HDelete)(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F]
  ): F[Unit] =
    FE.map(F.parallel(t.delete(a)))(_ => ())

  def append[F[_]](t: AsyncTableT, a: HAppend)(
      implicit
      F: Par[F]
  ): F[HResult] =
    F.parallel(t.append(a))

  def increment[F[_]](t: AsyncTableT, a: HIncrement)(
      implicit
      F: Par[F]
  ): F[HResult] =
    F.parallel(t.increment(a))

  def batch[F[_], C[_]](t: AsyncTableT, as: Seq[_ <: HRow])(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F],
      factory: Factory[BatchResult, C[BatchResult]]
  ): F[C[BatchResult]] = {
    val itr   = as.iterator
    val itcfo = t.batch[Object](as.asJava).iterator.asScala
    val itfb = itr
      .zip(itcfo.map(F.parallel.apply))
      .map {
        case (a, fo) =>
          FE.recoverWith(FE.map[Object, BatchResult](fo) {
            case r: HResult =>
              BatchResult.Mutate(Some(r))
            case null =>
              a match {
                case _: HGet | _: HAppend | _: HIncrement | _: RowMutations =>
                  BatchResult.Mutate(None)
                case _ => // Delete or Put
                  BatchResult.VoidMutate
              }
            case other =>
              BatchResult.Error(new Exception(s"Unexpected class returned: ${other.getClass.getSimpleName}"), a)
          }) {
            case t: Throwable =>
              FE.pure(BatchResult.Error(t, a))
          }
      }
    val fbb = itfb.foldLeft(FE.pure(factory.newBuilder)) {
      case (acc, fb) => FE.map2(fb, acc)((a, b) => b += a)
    }
    FE.map(fbb)(_.result)
  }

  def batchAll[F[_], C[_]](t: AsyncTableT, as: Seq[_ <: HRow])(
      implicit
      FE: ApplicativeError[F, Throwable],
      F: Par[F],
      factory: Factory[Option[HResult], C[Option[HResult]]]
  ): F[C[Option[HResult]]] =
    FE.map(F.parallel(t.batchAll[Object](as.asJava))) { xs =>
      val it = xs.iterator
      val c  = factory.newBuilder
      while (it.hasNext) c += (it.next match { case r: HResult => Option(r); case null => None })
      c.result
    }

  def kleisli[F[_], A](f: AsyncTableT => F[A]): Kleisli[F, AsyncTableT, A] =
    Kleisli[F, AsyncTableT, A](f)
}
